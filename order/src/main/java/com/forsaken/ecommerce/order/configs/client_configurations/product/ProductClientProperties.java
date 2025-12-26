package com.forsaken.ecommerce.order.configs.client_configurations.product;

import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.time.DurationMax;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "application.config.product")
public record ProductClientProperties(
        @NotNull(message = "Product service URL must not be null")
        URI url,

        @NotNull
        @DurationMax(seconds = 60, message = "Connect timeout must not exceed 60 seconds")
        Duration connectTimeout,

        @NotNull
        @DurationMax(seconds = 60, message = "Read timeout must not exceed 60 seconds")
        Duration readTimeout
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
        validateScheme(url);
    }

    private static void validateScheme(final URI url) {
        final String scheme = url.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException(
                    "Only HTTP/HTTPS URLs are supported for product service. Found: " + scheme
            );
        }
    }
}

