package com.forsaken.ecommerce.product.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

/**
 * Order-level reservation record. Its primary key serializes reserve/release per order
 * reference, and a RELEASED record also acts as a tombstone that blocks late reservations.
 */
@Entity
@Getter
@NoArgsConstructor
@Table(name = "product_reservation_order")
public class ReservationOrder implements Persistable<String> {

    @Id
    @Column(name = "order_reference", nullable = false)
    private String orderReference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationStatus status;

    /** Forces insert-only saves so a concurrent tombstone is never merged over. */
    @Transient
    private boolean isNew = true;

    @Override
    public String getId() {
        return orderReference;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }

    public ReservationOrder(final String orderReference, final ReservationStatus status) {
        this.orderReference = orderReference;
        this.status = status;
    }

    public void release() {
        this.status = ReservationStatus.RELEASED;
    }
}
