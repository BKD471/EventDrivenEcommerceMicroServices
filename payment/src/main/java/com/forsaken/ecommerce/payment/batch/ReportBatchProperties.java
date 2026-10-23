package com.forsaken.ecommerce.payment.batch;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/** Tuning for the daily payment summary batch job ({@code payment.report.batch.*}). */
@Validated
@ConfigurationProperties(prefix = "payment.report.batch")
public record ReportBatchProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("7") @Min(1) int chunkSize,
        @DefaultValue("3") @Min(1) int retryLimit,
        @DefaultValue("5") @Min(0) int skipLimit,
        /** Each run regenerates this many most recent complete days, so late confirmations are picked up. */
        @DefaultValue("3") @Min(1) int lookbackDays
) {
}
