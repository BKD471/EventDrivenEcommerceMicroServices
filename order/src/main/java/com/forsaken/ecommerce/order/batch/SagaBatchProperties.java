package com.forsaken.ecommerce.order.batch;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/** Tuning for the saga recovery batch jobs ({@code order.saga.batch.*}). */
@Validated
@ConfigurationProperties(prefix = "order.saga.batch")
public record SagaBatchProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("50") @Min(1) int chunkSize,
        @DefaultValue("3") @Min(1) int retryLimit,
        @DefaultValue("100") @Min(0) int skipLimit
) {
}
