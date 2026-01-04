package com.forsaken.ecommerce.payment.service;


import com.forsaken.ecommerce.avro.PaymentConfirmation;
import com.forsaken.ecommerce.avro.PaymentMethod;
import com.forsaken.ecommerce.common.responses.PagedResponse;
import com.forsaken.ecommerce.payment.dto.PaymentRequest;
import com.forsaken.ecommerce.payment.dto.PaymentSummaryDto;
import com.forsaken.ecommerce.payment.model.Payment;
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

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentServiceImpl implements IPaymentService {

    private final IPaymentRepository repository;
    private final INotificationProducerService notificationProducer;
    private final Tracer tracer;

    @Override
    @CacheEvict(
            value = {"paymentSummaries", "paymentsByDate"},
            allEntries = true
    )
    public Integer createPayment(final PaymentRequest request) {
        final Payment payment = this.repository.save(request.toPayment());
        final LocalDateTime localDateTime = LocalDateTime.now();
        final Instant instant = localDateTime.atZone(ZoneId.of("UTC")).toInstant();
        final String traceId = null != tracer.currentSpan()
                ? tracer.currentSpan().context().traceId()
                : "NO_TRACE";

        this.notificationProducer.sendNotification(
                new PaymentConfirmation(
                        request.orderReference(),
                        convertBigDecimalToBytes(request.amount()),
                        PaymentMethod.valueOf(request.paymentMethod().name()),
                        request.customer().firstname(),
                        request.customer().lastname(),
                        request.customer().email(),
                        instant,
                        traceId
                )
        );
        log.info("Created Payment Request: {}", request);
        return payment.getId();
    }

    @Override
    @Cacheable(
            value = "paymentSummaries",
            key = "{#fromDate ?: 'START', #toDate ?: 'NOW', #page, #size}"
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
            key = "{#fromDate ?: 'START', #toDate ?: 'NOW', #page, #size}"
    )
    public PagedResponse<Payment> getAllPayments(
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

        return PagedResponse.<Payment>builder()
                .content(paymentPage.getContent())
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
