package com.forsaken.ecommerce.order.configs.client_configurations.product;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "application.config.product")
public record ProductClientProperties(
        @NotBlank(message = "Product service URL must not be blank") String url,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout
) {

    public ProductClientProperties {
        if (connectTimeout.isZero() || connectTimeout.isNegative()) {
            throw new IllegalArgumentException(
                    "application.config.product.connectTimeout must be positive"
            );
        }
        if (readTimeout.isZero() || readTimeout.isNegative()) {
            throw new IllegalArgumentException(
                    "application.config.product.readTimeout must be positive"
            );
        }
    }
}

