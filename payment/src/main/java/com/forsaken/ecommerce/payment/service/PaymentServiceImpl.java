package com.forsaken.ecommerce.payment.service;


import com.forsaken.ecommerce.avro.PaymentConfirmation;
import com.forsaken.ecommerce.avro.PaymentMethod;
import com.forsaken.ecommerce.common.responses.PagedResponse;
import com.forsaken.ecommerce.payment.dto.PaymentDto;
import com.forsaken.ecommerce.payment.dto.PaymentRequest;
import com.forsaken.ecommerce.payment.dto.PaymentSummaryDto;
import com.forsaken.ecommerce.payment.model.Payment;
import com.forsaken.ecommerce.payment.model.PaymentStatus;
import com.forsaken.ecommerce.payment.repository.IPaymentRepository;
import com.forsaken.ecommerce.payment.repository.PaymentSummary;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.Conversions;
import org.apache.avro.LogicalTypes;
import org.apache.avro.Schema;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentServiceImpl implements IPaymentService {

    private final IPaymentRepository repository;
    private final INotificationProducerService notificationProducer;
    private final Tracer tracer;

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
    @Transactional
    @CacheEvict(
            value = {"paymentSummaries", "paymentsByDate"},
            allEntries = true
    )
    public Integer createPayment(final PaymentRequest request) {
        final Optional<Payment> existing = repository.findByOrderId(request.orderId());
        if (existing.isPresent()) {
            final Payment payment = existing.get();
            if (payment.getAmount().compareTo(request.amount()) != 0
                    || payment.getPaymentMethod() != request.paymentMethod()
                    || !payment.getOrderReference().equals(request.orderReference())) {
                throw new IllegalArgumentException("Order ID already has a different payment request");
            }
            return payment.getId();
        }
        final Payment payment = this.repository.save(request.toPayment());
        log.info("Authorized payment for orderId={}", request.orderId());
        return payment.getId();
    }

    @Override
    @Transactional
    @CacheEvict(value = {"paymentSummaries", "paymentsByDate"}, allEntries = true)
    public void confirmPayment(final Integer orderId) {
        final Payment payment = repository.findByOrderId(orderId)
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

        final Instant instant = LocalDateTime.now().atZone(ZoneId.of("UTC")).toInstant();
        final String traceId = (null != tracer && null != tracer.currentSpan())
                ? tracer.currentSpan().context().traceId()
                : "NO_TRACE";
        this.notificationProducer.sendNotification(
                new PaymentConfirmation(
                        payment.getOrderReference(),
                        convertBigDecimalToBytes(payment.getAmount()),
                        payment.getPaymentMethod(),
                        payment.getCustomerFirstname(),
                        payment.getCustomerLastname(),
                        payment.getCustomerEmail(),
                        instant,
                        traceId
                )
        );
        log.info("Confirmed payment for orderId={}", orderId);
    }

    @Override
    @Transactional
    @CacheEvict(value = {"paymentSummaries", "paymentsByDate"}, allEntries = true)
    public void cancelPayment(final Integer orderId) {
        repository.findByOrderId(orderId).ifPresent(payment -> {
            if (payment.getStatus() != PaymentStatus.CANCELLED) {
                payment.setStatus(PaymentStatus.CANCELLED);
                repository.save(payment);
            }
        });
        log.info("Cancelled payment for orderId={}", orderId);
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

    private ByteBuffer convertBigDecimalToBytes(final BigDecimal value) {
        if (null == value) return null;

        final Schema DECIMAL_SCHEMA =
                LogicalTypes.decimal(18, 2)
                        .addToSchema(Schema.create(Schema.Type.BYTES));
        final Conversions.DecimalConversion DECIMAL_CONVERSION =
                new Conversions.DecimalConversion();

        return DECIMAL_CONVERSION.toBytes(value, DECIMAL_SCHEMA, DECIMAL_SCHEMA.getLogicalType());
    }
}