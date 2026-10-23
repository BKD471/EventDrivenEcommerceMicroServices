package com.forsaken.ecommerce.order.batch;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** Tuning for the reconciliation batch jobs ({@code order.reconciliation.batch.*}). */
@Validated
@ConfigurationProperties(prefix = "order.reconciliation.batch")
public record ReconciliationProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("100") @Min(1) int chunkSize,
        @DefaultValue("3") @Min(1) int retryLimit,
        @DefaultValue("100") @Min(0) int skipLimit,
        /** Records younger than this are ignored, so in-flight sagas are not reported as inconsistent. */
        @DefaultValue("1h") Duration minAge
) {
}
