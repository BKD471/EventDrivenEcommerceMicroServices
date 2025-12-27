package com.forsaken.ecommerce.order.order.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.forsaken.ecommerce.order.order.model.Order;
import com.forsaken.ecommerce.order.order.model.PaymentMethod;
import com.forsaken.ecommerce.order.product.PurchaseRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

@Builder
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record OrderRequest(
        Integer id,

        @NotNull
        String reference,

        @Positive(message = "Order amount should be positive")
        BigDecimal amount,

        @NotNull(message = "Payment method should be precised")
        PaymentMethod paymentMethod,

        @NotBlank(message = "Customer should be present")
        String customerId,

        @NotEmpty(message = "You should at least purchase one product")
        List<PurchaseRequest> products
) {

    public Order toOrder() {
        return Order.builder()
                .id(this.id())
                .reference(this.reference())
                .paymentMethod(this.paymentMethod())
                .customerId(this.customerId())
                .build();
    }
}
