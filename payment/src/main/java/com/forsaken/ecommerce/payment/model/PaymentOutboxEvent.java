package com.forsaken.ecommerce.payment.model;

import com.forsaken.ecommerce.avro.PaymentMethod;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Payment confirmation event stored in the same transaction as the payment status change,
 * and published to Kafka only after that transaction has committed.
 */
@Entity
@Getter
@NoArgsConstructor
@Table(name = "payment_outbox_event")
public class PaymentOutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String orderReference;

    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    private PaymentMethod paymentMethod;

    private String customerFirstname;
    private String customerLastname;
    private String customerEmail;

    @Column(nullable = false)
    private Instant eventTime;

    private String traceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutboxStatus status = OutboxStatus.PENDING;

    public PaymentOutboxEvent(final Payment payment, final Instant eventTime, final String traceId) {
        this.orderReference = payment.getOrderReference();
        this.amount = payment.getAmount();
        this.paymentMethod = payment.getPaymentMethod();
        this.customerFirstname = payment.getCustomerFirstname();
        this.customerLastname = payment.getCustomerLastname();
        this.customerEmail = payment.getCustomerEmail();
        this.eventTime = eventTime;
        this.traceId = traceId;
    }

    private int attempts;

    public void markSent() {
        this.status = OutboxStatus.SENT;
    }

    /** Counts a failed publish; after maxAttempts the event is quarantined so it cannot block others. */
    public void recordFailure(final int maxAttempts) {
        this.attempts++;
        if (this.attempts >= maxAttempts) {
            this.status = OutboxStatus.QUARANTINED;
        }
    }

    public enum OutboxStatus {
        PENDING,
        SENT,
        QUARANTINED
    }
}
