package com.forsaken.ecommerce.order.product;

import java.time.Instant;

/** Reservation state returned by the product service for read-only reconciliation. */
public record ProductReservationReconciliationItem(
        String orderReference,
        String status,
        Instant createdAt,
        Instant confirmedAt,
        Instant releasedAt
) {
}
