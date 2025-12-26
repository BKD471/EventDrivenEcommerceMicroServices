package com.forsaken.ecommerce.order.configs.client_configurations.payment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "application.config.payment")
public record PaymentClientProperties(
        @NotBlank(message = "Payment service URL must not be blank") String url,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout
) {

    public PaymentClientProperties {
        if (connectTimeout.isZero() || connectTimeout.isNegative()) {
            throw new IllegalArgumentException(
                    "application.config.payment.connectTimeout must be positive"
            );
        }
        if (readTimeout.isZero() || readTimeout.isNegative()) {
            throw new IllegalArgumentException(
                    "application.config.payment.readTimeout must be positive"
            );
        }
    }
}
