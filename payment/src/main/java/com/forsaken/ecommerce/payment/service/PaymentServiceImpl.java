package com.forsaken.ecommerce.payment.service;


import com.forsaken.ecommerce.common.responses.PagedResponse;
import com.forsaken.ecommerce.payment.dto.PaymentDto;
import com.forsaken.ecommerce.payment.dto.PaymentRequest;
import com.forsaken.ecommerce.payment.dto.PaymentSummaryDto;
import com.forsaken.ecommerce.payment.model.Payment;
import com.forsaken.ecommerce.payment.model.PaymentOutboxEvent;
import com.forsaken.ecommerce.payment.model.PaymentStatus;
import com.forsaken.ecommerce.payment.repository.IPaymentOutboxRepository;
import com.forsaken.ecommerce.payment.repository.IPaymentRepository;
import com.forsaken.ecommerce.payment.repository.PaymentSummary;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Supplier;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentServiceImpl implements IPaymentService {

    private static final int MAX_CONFLICT_ATTEMPTS = 3;

    private final IPaymentRepository repository;
    private final IPaymentOutboxRepository outboxRepository;
    private final Tracer tracer;
    private final TransactionTemplate transactionTemplate;

    /**
     * Payment creation mutates the underlying dataset in a way that affects
     * all aggregated summaries and date-based listings.
     *
     * Because cache keys are derived from arbitrary date ranges and pagination
     * parameters, targeted eviction is not feasible.
     *
     * Therefore, all related caches are fully invalidated to guarantee
     * consistency over stale or incorrect financial data.
     */
    @Override
    @CacheEvict(
            value = {"paymentSummaries", "paymentsByDate"},
            allEntries = true
    )
    public Integer createPayment(final PaymentRequest request) {
        return executeWithConflictRetry(() -> authorize(request));
    }

    private Integer authorize(final PaymentRequest request) {
        validateConfirmationAmount(request.amount());
        final Optional<Payment> existing = repository.findByOrderIdForUpdate(request.orderId());
        if (existing.isPresent()) {
            final Payment payment = existing.get();
            if (payment.getStatus() == PaymentStatus.CANCELLED) {
                throw new IllegalStateException("Payment for order " + request.orderId() + " was cancelled");
            }
            if (payment.getAmount().compareTo(request.amount()) != 0
                    || payment.getPaymentMethod() != request.paymentMethod()
                    || (payment.getOrderReference() != null
                    && !payment.getOrderReference().equals(request.orderReference()))) {
                throw new IllegalArgumentException("Order ID already has a different payment request");
            }
            return payment.getId();
        }
        final Payment payment = this.repository.saveAndFlush(request.toPayment());
        log.info("Authorized payment for orderId={}", request.orderId());
        return payment.getId();
    }

    @Override
    @Transactional
    @CacheEvict(value = {"paymentSummaries", "paymentsByDate"}, allEntries = true)
    public void confirmPayment(final Integer orderId) {
        final Payment payment = repository.findByOrderIdForUpdate(orderId)
                .orElseThrow(() -> new NoSuchElementException("No payment found for order " + orderId));
        if (payment.getStatus() == null) {
            // Payments created before saga status tracking were confirmed on creation.
            payment.setStatus(PaymentStatus.COMPLETED);
            repository.save(payment);
            return;
        }
        if (payment.getStatus() == PaymentStatus.COMPLETED) {
            return;
        }
        if (payment.getStatus() != PaymentStatus.AUTHORIZED) {
            throw new IllegalStateException("Cannot confirm payment in state " + payment.getStatus());
        }
        payment.setStatus(PaymentStatus.COMPLETED);
        repository.save(payment);

        final String traceId = (null != tracer && null != tracer.currentSpan())
                ? tracer.currentSpan().context().traceId()
                : "NO_TRACE";
        outboxRepository.save(new PaymentOutboxEvent(
                payment,
                LocalDateTime.now().atZone(ZoneId.of("UTC")).toInstant(),
                traceId
        ));
        log.info("Confirmed payment for orderId={}", orderId);
    }

    @Override
    @CacheEvict(value = {"paymentSummaries", "paymentsByDate"}, allEntries = true)
    public void cancelPayment(final Integer orderId) {
        executeWithConflictRetry(() -> {
            final Optional<Payment> existing = repository.findByOrderIdForUpdate(orderId);
            if (existing.isEmpty()) {
                // Durable cancellation intent: a late authorization for this order will be rejected.
                repository.saveAndFlush(Payment.builder()
                        .orderId(orderId)
                        .status(PaymentStatus.CANCELLED)
                        .build());
            } else if (existing.get().getStatus() == null
                    || existing.get().getStatus() == PaymentStatus.COMPLETED) {
                throw new IllegalStateException(
                        "Cannot cancel a confirmed payment for order " + orderId
                );
            } else if (existing.get().getStatus() != PaymentStatus.CANCELLED) {
                existing.get().setStatus(PaymentStatus.CANCELLED);
                repository.save(existing.get());
            }
            return null;
        });
        log.info("Cancelled payment for orderId={}", orderId);
    }

    /** Rejects amounts that could never be encoded in the Avro confirmation event. */
    private void validateConfirmationAmount(final BigDecimal amount) {
        try {
            if (amount.setScale(2, RoundingMode.UNNECESSARY).precision() > 18) {
                throw new ArithmeticException("precision");
            }
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException(
                    "Payment amount must have at most 2 decimal places and 18 digits", ex
            );
        }
    }

    /**
     * Runs the work in a transaction. A concurrent first insert for the same order ID surfaces
     * as a unique-key violation; the retry then finds and locks the winning row.
     */
    private <T> T executeWithConflictRetry(final Supplier<T> work) {
        for (int attempt = 1; ; attempt++) {
            try {
                return transactionTemplate.execute(status -> work.get());
            } catch (DataIntegrityViolationException ex) {
                if (attempt >= MAX_CONFLICT_ATTEMPTS) {
                    throw ex;
                }
            }
        }
    }

    @Override
    @Cacheable(
            value = "paymentSummaries",
            key = "{#fromDate != null ? #fromDate.toString() : 'START', " +
                    "#toDate != null ? #toDate.toString() : 'NOW', " +
                    "#page, #size}"
    )
    public PagedResponse<PaymentSummaryDto> getPaymentSummary(
            final LocalDateTime fromDate,
            final LocalDateTime toDate,
            final int page,
            final int size
    ) {
        final int safePage = Math.max(page - 1, 0);
        final int safeSize = size <= 0 ? 10 : size; // default size if needed
        final Pageable pageable = PageRequest.of(safePage, safeSize);
        log.info("Get Payment Summary By Date: from={}, to={}, page={}, size={}",
                fromDate, toDate, safePage, safeSize);

        final Page<PaymentSummary> summaryPage =
                repository.findPaymentSummaryBetween(fromDate, toDate, pageable);
        final List<PaymentSummaryDto> content = summaryPage.getContent().stream()
                .map(p -> new PaymentSummaryDto(
                        p.getPaymentMethod(),
                        p.getCount(),
                        p.getTotalAmount()
                ))
                .toList();
        return PagedResponse.<PaymentSummaryDto>builder()
                .content(content)
                .page(summaryPage.getNumber())
                .size(summaryPage.getSize())
                .totalElements(summaryPage.getTotalElements())
                .totalPages(summaryPage.getTotalPages())
                .build();
    }

    @Override
    @Cacheable(
            value = "paymentsByDate",
            key = "{#fromDate != null ? #fromDate.toString() : 'START', " +
                    "#toDate != null ? #toDate.toString() : 'NOW', " +
                    "#page, #size}"
    )
    public PagedResponse<PaymentDto> getAllPayments(
            final LocalDateTime fromDate,
            final LocalDateTime toDate,
            final int page,
            final int size
    ) {
        log.info("Get All Payments By Date: from={}, to={}, page={}, size={}",
                fromDate, toDate, page, size);

        final int safePage = Math.max(page - 1, 0);
        final int safeSize = size <= 0 ? 5 : size;
        final Pageable pageable = PageRequest.of(safePage, safeSize);
        final Page<Payment> paymentPage = repository.findAllByCreatedDateBetween(
                fromDate,
                toDate,
                pageable
        );

        return PagedResponse.<PaymentDto>builder()
                .content(paymentPage.getContent()
                        .stream()
                        .map(PaymentDto::from).toList()
                )
                .page(paymentPage.getNumber() + 1)
                .size(paymentPage.getSize())
                .totalElements(paymentPage.getTotalElements())
                .totalPages(paymentPage.getTotalPages())
                .build();
    }
}
