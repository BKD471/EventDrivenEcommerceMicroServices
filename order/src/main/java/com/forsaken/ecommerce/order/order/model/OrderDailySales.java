package com.forsaken.ecommerce.order.order.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Pre-aggregated COMPLETED orders for one day and payment method, written by the daily sales job. */
@AllArgsConstructor
@Builder
@Getter
@Entity
@NoArgsConstructor
@Table(
        name = "order_daily_sales",
        uniqueConstraints = @UniqueConstraint(columnNames = {"sales_date", "payment_method"})
)
public class OrderDailySales {

    @Id
    @GeneratedValue
    private Long id;

    @Column(name = "sales_date", nullable = false)
    private LocalDate salesDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false)
    private PaymentMethod paymentMethod;

    @Column(nullable = false)
    private long orderCount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false)
    private LocalDateTime generatedAt;
}
