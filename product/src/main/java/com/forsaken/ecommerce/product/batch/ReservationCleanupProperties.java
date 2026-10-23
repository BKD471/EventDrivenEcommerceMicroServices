package com.forsaken.ecommerce.product.batch;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** Tuning for the reservation cleanup batch jobs ({@code product.reservation.batch.*}). */
@Validated
@ConfigurationProperties(prefix = "product.reservation.batch")
public record ReservationCleanupProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("100") @Min(1) int chunkSize,
        @DefaultValue("3") @Min(1) int retryLimit,
        @DefaultValue("100") @Min(0) int skipLimit,
        /** A RESERVED reservation must be at least this old before expiry is considered. */
        @NotNull @DefaultValue("1h") Duration expiryAge,
        /**
         * A RELEASED tombstone is kept this long. It blocks late reservations for the order reference, so it must
         * exceed the longest time an order saga can still be retried.
         */
        @NotNull @DefaultValue("30d") Duration tombstoneRetention
) {
}
