package com.forsaken.ecommerce.payment.dto;

import com.forsaken.ecommerce.avro.PaymentMethod;
import com.forsaken.ecommerce.payment.model.PaymentDailySummary;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PaymentDailySummaryDto(
        LocalDate date, PaymentMethod paymentMethod, long paymentCount, BigDecimal totalAmount) {

    public static PaymentDailySummaryDto from(final PaymentDailySummary summary) {
        return new PaymentDailySummaryDto(summary.getSummaryDate(), summary.getPaymentMethod(),
                summary.getPaymentCount(), summary.getTotalAmount());
    }
}
