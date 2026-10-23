package com.forsaken.ecommerce.notification.controller;

import com.forsaken.ecommerce.notification.models.PaymentMethod;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Payment data needed to rebuild an invoice that was never stored. */
public record InvoiceRegenerationRequest(
        @NotBlank String customerName,
        @NotBlank @Email String customerEmail,
        @NotNull @Positive BigDecimal amount,
        @NotNull PaymentMethod paymentMethod,
        @NotNull LocalDateTime paymentDate
) {
}
