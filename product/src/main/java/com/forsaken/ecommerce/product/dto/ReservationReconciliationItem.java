package com.forsaken.ecommerce.product.dto;

import com.forsaken.ecommerce.product.model.ReservationStatus;

import java.time.Instant;

/** Read-only reservation state exposed to the order service for consistency checks. */
public record ReservationReconciliationItem(
        String orderReference,
        ReservationStatus status,
        Instant createdAt,
        Instant confirmedAt,
        Instant releasedAt
) {
}
