package com.forsaken.ecommerce.order.configs.client_configurations.customer;

import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.time.DurationMax;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "application.config.customer")
public record CustomerClientProperties(
        @NotNull(message = "Customer service URL must not be null")
        URI url,

        @NotNull
        @DurationMax(seconds = 60, message = "Connect timeout must not exceed 60 seconds")
        Duration connectTimeout,

        @NotNull
        @DurationMax(seconds = 60, message = "Read timeout must not exceed 60 seconds")
        Duration readTimeout
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

    @PostConstruct
    void validateScheme() {
        final String scheme = url.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException(
                    "Only HTTP/HTTPS URLs are supported for customer service. Found: " + scheme
            );
        }
    }
}
