package com.forsaken.ecommerce.payment.service;

import com.forsaken.ecommerce.payment.dto.PaymentDailySummaryDto;
import com.forsaken.ecommerce.payment.model.PaymentDailySummary;
import com.forsaken.ecommerce.payment.repository.IPaymentDailySummaryRepository;
import com.forsaken.ecommerce.payment.repository.IPaymentRepository;
import com.forsaken.ecommerce.payment.repository.PaymentSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Builds and serves the daily COMPLETED-payment summaries. Regenerating a day replaces it, so runs are idempotent. */
@Service
@RequiredArgsConstructor
public class PaymentReportService {

    private final IPaymentRepository paymentRepository;
    private final IPaymentDailySummaryRepository summaryRepository;

    @Transactional
    public int summarizeDay(final LocalDate date) {
        final LocalDateTime now = LocalDateTime.now();
        final List<PaymentDailySummary> rows = paymentRepository
                .summarizeCompletedBetween(date.atStartOfDay(), date.plusDays(1).atStartOfDay())
                .stream()
                .map(summary -> toRow(date, summary, now))
                .toList();
        summaryRepository.deleteBySummaryDate(date);
        summaryRepository.saveAll(rows);
        return rows.size();
    }

    @Transactional(readOnly = true)
    public List<PaymentDailySummaryDto> findDailySummaries(final LocalDate from, final LocalDate to) {
        return summaryRepository.findBySummaryDateBetweenOrderBySummaryDateAscPaymentMethodAsc(from, to)
                .stream()
                .map(PaymentDailySummaryDto::from)
                .toList();
    }

    private static PaymentDailySummary toRow(
            final LocalDate date, final PaymentSummary summary, final LocalDateTime now) {
        return PaymentDailySummary.builder()
                .summaryDate(date)
                .paymentMethod(summary.getPaymentMethod())
                .paymentCount(summary.getCount())
                .totalAmount(summary.getTotalAmount() == null ? BigDecimal.ZERO : summary.getTotalAmount())
                .generatedAt(now)
                .build();
    }
}
