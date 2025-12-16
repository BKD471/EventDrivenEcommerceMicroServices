package com.forsaken.ecommerce.notification.configs.invoice;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "invoice")
public record InvoiceProperties(
        @NotBlank
        String downLoadUrl
) {
}
