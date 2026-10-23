package com.forsaken.ecommerce.payment.batch;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;

/** Settings for the invoice backfill job and its notification service client ({@code payment.invoice.batch.*}). */
@Validated
@ConfigurationProperties(prefix = "payment.invoice.batch")
public record InvoiceBatchProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("50") @Min(1) int chunkSize,
        @DefaultValue("3") @Min(1) int retryLimit,
        @DefaultValue("100") @Min(0) int skipLimit,
        /** How far back COMPLETED payments are checked for a missing invoice. */
        @NotNull @DefaultValue("7d") Duration lookback,
        /** Payments younger than this are left alone because their invoice may still be in flight. */
        @NotNull @DefaultValue("15m") Duration minAge,
        @NotNull @DefaultValue("http://localhost:8040/api/v1/invoices") URI notificationUrl,
        /** Shared secret for the notification service's internal invoice endpoints. Blank disables the job. */
        @DefaultValue("") String token,
        @NotNull @DefaultValue("5s") Duration connectTimeout,
        @NotNull @DefaultValue("30s") Duration readTimeout
) {
}
