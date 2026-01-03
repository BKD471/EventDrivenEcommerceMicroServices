package com.forsaken.ecommerce.customer.configs.redis;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.cache")
public record RedisProperties(
        @Max(60)
        @Min(10)
        Long ttlMinutes
) {
}