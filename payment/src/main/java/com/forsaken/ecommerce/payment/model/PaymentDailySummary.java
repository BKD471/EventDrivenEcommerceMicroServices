package com.forsaken.ecommerce.payment.model;

import com.forsaken.ecommerce.avro.PaymentMethod;
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

/** Pre-aggregated COMPLETED payments for one day and payment method, written by the daily summary job. */
@AllArgsConstructor
@Builder
@Getter
@Entity
@NoArgsConstructor
@Table(
        name = "payment_daily_summary",
        uniqueConstraints = @UniqueConstraint(columnNames = {"summary_date", "payment_method"})
)
public class PaymentDailySummary {

    @Id
    @GeneratedValue
    private Long id;

    @Column(name = "summary_date", nullable = false)
    private LocalDate summaryDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false)
    private PaymentMethod paymentMethod;

    @Column(nullable = false)
    private long paymentCount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false)
    private LocalDateTime generatedAt;
}
