package com.forsaken.ecommerce.order.order.model;

import java.util.List;

public enum OrderSagaStatus {
    PENDING,
    /** Payment is confirmed; only the order confirmation publication remains (forward-only, never compensated). */
    CONFIRMING,
    COMPLETED,
    FAILED,
    COMPENSATION_REQUIRED;

    /** Unfinished sagas that recovery resumes forward. */
    public static final List<OrderSagaStatus> RECOVERABLE = List.of(PENDING, CONFIRMING);

    /** Sagas whose compensation still has to be completed. */
    public static final List<OrderSagaStatus> COMPENSATION_PENDING = List.of(COMPENSATION_REQUIRED);
}
