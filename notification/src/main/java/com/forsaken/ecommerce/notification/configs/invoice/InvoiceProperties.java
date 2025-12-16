package com.forsaken.ecommerce.notification.configs.invoice;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "invoice")
public record InvoiceProperties(
        @NotBlank
        String downloadUrl,

        @NotBlank
        String senderEmailAddress,

        @NotBlank
        String jasperTemplatePath,

        @NotBlank
        String companyLogoPath,

        @NotBlank
        String userLogoPath,

        @NotBlank
        String emailLogoPath,

        @NotBlank
        String amountLogoPath,

        @NotBlank
        String paymentLogoPath,

        @NotBlank
        String calendarLogoPath
) {
}
