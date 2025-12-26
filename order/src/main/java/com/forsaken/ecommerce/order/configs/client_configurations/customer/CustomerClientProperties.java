package com.forsaken.ecommerce.order.configs.client_configurations.customer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "application.config.customer")
public record CustomerClientProperties(
        @NotBlank(message = "Customer service URL must not be blank") String url,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout
) {

    public CustomerClientProperties {
        if (connectTimeout.isZero() || connectTimeout.isNegative()) {
            throw new IllegalArgumentException(
                    "application.config.customer.connectTimeout must be positive"
            );
        }
        if (readTimeout.isZero() || readTimeout.isNegative()) {
            throw new IllegalArgumentException(
                    "application.config.customer.readTimeout must be positive"
            );
        }
    }
}
