package com.forsaken.ecommerce.payment.service;

import com.forsaken.ecommerce.avro.PaymentMethod;
import com.forsaken.ecommerce.payment.model.PaymentDailySummary;
import com.forsaken.ecommerce.payment.repository.IPaymentDailySummaryRepository;
import com.forsaken.ecommerce.payment.repository.IPaymentRepository;
import com.forsaken.ecommerce.payment.repository.PaymentSummary;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentReportServiceTest {

    private final IPaymentRepository payments = mock(IPaymentRepository.class);
    private final IPaymentDailySummaryRepository summaries = mock(IPaymentDailySummaryRepository.class);
    private final PaymentReportService service = new PaymentReportService(payments, summaries);

    private static PaymentSummary summary(final PaymentMethod method, final long count, final BigDecimal total) {
        final PaymentSummary summary = mock(PaymentSummary.class);
        when(summary.getPaymentMethod()).thenReturn(method);
        when(summary.getCount()).thenReturn(count);
        when(summary.getTotalAmount()).thenReturn(total);
        return summary;
    }

    @Test
    @SuppressWarnings("unchecked")
    void summarizeDayReplacesTheDayWithFreshAggregates() {
        final LocalDate day = LocalDate.of(2026, 1, 5);
        final PaymentSummary paypal = summary(PaymentMethod.PAYPAL, 3, new BigDecimal("30.00"));
        when(payments.summarizeCompletedBetween(day.atStartOfDay(), day.plusDays(1).atStartOfDay()))
                .thenReturn(List.of(paypal));

        final int rows = service.summarizeDay(day);

        assertEquals(1, rows);
        final InOrder order = inOrder(summaries);
        order.verify(summaries).deleteBySummaryDate(day);
        final ArgumentCaptor<List<PaymentDailySummary>> captor = ArgumentCaptor.forClass(List.class);
        order.verify(summaries).saveAll(captor.capture());
        final PaymentDailySummary saved = captor.getValue().get(0);
        assertEquals(day, saved.getSummaryDate());
        assertEquals(3, saved.getPaymentCount());
        assertEquals(new BigDecimal("30.00"), saved.getTotalAmount());
    }

    @Test
    void summarizeDayWithNoPaymentsClearsTheDay() {
        final LocalDate day = LocalDate.of(2026, 1, 5);
        when(payments.summarizeCompletedBetween(any(), any())).thenReturn(List.of());

        assertEquals(0, service.summarizeDay(day));
        verify(summaries).deleteBySummaryDate(day);
    }
}
