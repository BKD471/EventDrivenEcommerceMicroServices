package com.forsaken.ecommerce.order.order.model;

public enum OrderSagaStatus {
    PENDING,
    COMPLETED,
    FAILED,
    COMPENSATION_REQUIRED
}
