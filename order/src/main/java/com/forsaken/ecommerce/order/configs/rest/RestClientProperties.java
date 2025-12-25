package com.forsaken.ecommerce.order.configs.rest;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "rest.client")
public record RestClientProperties(

        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout
) {
    public RestClientProperties {
        if (connectTimeout.isZero() || connectTimeout.isNegative()) {
            throw new IllegalArgumentException(
                    "rest.client.connect-timeout must be positive"
            );
        }
        if (readTimeout.isZero() || readTimeout.isNegative()) {
            throw new IllegalArgumentException(
                    "rest.client.read-timeout must be positive"
            );
        }
    }
}