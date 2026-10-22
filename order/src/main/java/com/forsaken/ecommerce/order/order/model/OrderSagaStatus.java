package com.forsaken.ecommerce.order.order.model;

public enum OrderSagaStatus {
    PENDING,
    /** Payment is confirmed; only the order confirmation publication remains (forward-only, never compensated). */
    CONFIRMING,
    COMPLETED,
    FAILED,
    COMPENSATION_REQUIRED
}
