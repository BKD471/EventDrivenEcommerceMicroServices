package com.forsaken.ecommerce.payment.dto;

import com.forsaken.ecommerce.avro.PaymentMethod;
import com.forsaken.ecommerce.payment.model.Payment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Cache-safe and API-safe representation of a Payment.
 *
 * <p>This DTO is intentionally decoupled from the JPA entity to:
 * <ul>
 *   <li>avoid caching JPA-managed objects in Redis</li>
 *   <li>prevent serialization issues with Hibernate proxies</li>
 *   <li>provide a stable contract for API and caching layers</li>
 * </ul>
 */
public record PaymentDto(
        Integer id,
        BigDecimal amount,
        PaymentMethod paymentMethod,
        Integer orderId,
        LocalDateTime createdDate
) {

    /**
     * Maps a {@link Payment} JPA entity to a cache-safe {@link PaymentDto}.
     *
     * @param payment the persisted Payment entity
     * @return immutable PaymentDto
     */
    public static PaymentDto from(final Payment payment) {
        return new PaymentDto(
                payment.getId(),
                payment.getAmount(),
                payment.getPaymentMethod(),
                payment.getOrderId(),
                payment.getCreatedDate()
        );
    }
}