package com.forsaken.ecommerce.order.order.service;

/** A cross-service inconsistency found by reconciliation. Findings are reported, never auto-repaired. */
public record ReconciliationFinding(Type type, Integer orderId, Integer paymentId, String detail) {

    public enum Type {
        COMPLETED_ORDER_WITHOUT_PAYMENT,
        COMPLETED_ORDER_PAYMENT_NOT_COMPLETED,
        AUTHORIZED_PAYMENT_WITHOUT_ORDER,
        AUTHORIZED_PAYMENT_FOR_FAILED_ORDER,
        COMPLETED_ORDER_WITHOUT_CONFIRMED_RESERVATION,
        FAILED_ORDER_WITH_UNRELEASED_RESERVATION,
        RESERVATION_WITHOUT_ORDER
    }
}
