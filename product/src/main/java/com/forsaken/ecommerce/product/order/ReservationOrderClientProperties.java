package com.forsaken.ecommerce.product.order;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;

/** Connection settings for the order service lookup used by reservation expiry. */
@Validated
@ConfigurationProperties(prefix = "application.config.order")
public record ReservationOrderClientProperties(
        @NotNull @DefaultValue("http://localhost:8222/api/v1/orders") URI url,
        @NotNull @DefaultValue("5s") Duration connectTimeout,
        @NotNull @DefaultValue("10s") Duration readTimeout
) {
}
