package com.forsaken.ecommerce.order.order.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.forsaken.ecommerce.order.order.model.PaymentMethod;
import com.forsaken.ecommerce.order.order.model.OrderSagaStatus;
import lombok.Builder;

import java.math.BigDecimal;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Builder
public record OrderResponse(
        Integer id,
        String reference,
        BigDecimal amount,
        PaymentMethod paymentMethod,
        String customerId,
        OrderSagaStatus sagaStatus
) {

}
