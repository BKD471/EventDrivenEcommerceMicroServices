package com.forsaken.ecommerce.product.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record ProductReservationRequest(
        @NotBlank String orderReference,
        @NotEmpty List<@Valid ProductPurchaseRequest> products
) {
}
