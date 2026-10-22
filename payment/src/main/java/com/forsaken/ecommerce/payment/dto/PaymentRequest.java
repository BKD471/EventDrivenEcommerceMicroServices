package com.forsaken.ecommerce.payment.dto;

import com.forsaken.ecommerce.avro.PaymentMethod;
import com.forsaken.ecommerce.payment.model.Payment;
import com.forsaken.ecommerce.payment.model.PaymentStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

@Validated
@Builder
public record PaymentRequest(
        Integer id,

        @NotNull
        @Min(value = 1, message = "Payment amount must be non zero")
        BigDecimal amount,

        @NotNull(message = "paymentMethod must be non null")
        PaymentMethod paymentMethod,

        @NotNull(message = "orderId must be non null")
        Integer orderId,

        @NotBlank(message = "orderReference should not be null or only whitespaces")
        String orderReference,

        @NotNull(message = "customer information must be non null")
        @Valid
        Customer customer
) {

    public Payment toPayment() {
        return Payment.builder()
                .id(this.id())
                .paymentMethod(this.paymentMethod())
                .amount(this.amount())
                .orderId(this.orderId())
                .orderReference(this.orderReference())
                .customerFirstname(this.customer().firstname())
                .customerLastname(this.customer().lastname())
                .customerEmail(this.customer().email())
                .status(PaymentStatus.AUTHORIZED)
                .build();
    }
}
