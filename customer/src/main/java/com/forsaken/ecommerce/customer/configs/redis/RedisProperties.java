package com.forsaken.ecommerce.customer.configs.redis;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for application-level Redis cache behavior.
 * <p>
 * These properties define cache-related policies such as default
 * time-to-live (TTL) and are intentionally separated from
 * {@code spring.redis.*} infrastructure settings.
 * <p>
 * The TTL value is applied uniformly to all Redis caches unless
 * overridden by a per-cache configuration.
 */
@Validated
@ConfigurationProperties(prefix = "app.cache")
public record RedisProperties(

        /**
         * Default cache time-to-live in minutes.
         * <p>
         * Controls how long cache entries remain valid in Redis before
         * automatic eviction. This helps balance cache freshness and
         * performance.
         * <p>
         * Valid range: 10–60 minutes.
         */
        @NotNull
        @Max(60)
        @Min(10)
        Long ttlMinutes
) {
}