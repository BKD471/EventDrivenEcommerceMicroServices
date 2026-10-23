package com.forsaken.ecommerce.payment.batch;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** Tuning for the payment outbox maintenance batch jobs ({@code payment.outbox.batch.*}). */
@Validated
@ConfigurationProperties(prefix = "payment.outbox.batch")
public record OutboxBatchProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("500") @Min(1) int chunkSize,
        @DefaultValue("3") @Min(1) int retryLimit,
        @DefaultValue("100") @Min(0) int skipLimit,
        /** Published events older than this are purged. */
        @DefaultValue("7d") Duration retention
) {
}
