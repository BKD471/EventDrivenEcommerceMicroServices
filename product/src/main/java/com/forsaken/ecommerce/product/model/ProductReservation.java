package com.forsaken.ecommerce.product.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Getter
@NoArgsConstructor
@Table(
        name = "product_reservation",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_product_reservation_order_product",
                columnNames = {"order_reference", "product_id"}
        )
)
public class ProductReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_reference", nullable = false)
    private String orderReference;

    @Column(name = "product_id", nullable = false)
    private Integer productId;

    @Column(nullable = false)
    private double quantity;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(nullable = false, precision = 38, scale = 2)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationStatus status;

    public ProductReservation(
            final String orderReference,
            final Product product,
            final double quantity
    ) {
        this.orderReference = orderReference;
        this.productId = product.getId();
        this.quantity = quantity;
        this.name = product.getName();
        this.description = product.getDescription();
        this.price = product.getPrice();
        this.status = ReservationStatus.RESERVED;
    }

    public void release() {
        this.status = ReservationStatus.RELEASED;
    }
}
