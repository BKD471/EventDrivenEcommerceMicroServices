package com.forsaken.ecommerce.payment.dto;

import com.forsaken.ecommerce.payment.model.Payment;
import com.forsaken.ecommerce.payment.model.PaymentStatus;

/** Minimal payment view used by cross-service reconciliation. */
public record PaymentReconciliationDto(Integer id, Integer orderId, PaymentStatus status) {

    public static PaymentReconciliationDto from(final Payment payment) {
        return new PaymentReconciliationDto(payment.getId(), payment.getOrderId(), payment.getStatus());
    }
}
