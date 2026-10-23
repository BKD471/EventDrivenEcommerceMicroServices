package com.forsaken.ecommerce.order.order.repository;

import com.forsaken.ecommerce.order.order.model.PaymentMethod;

import java.math.BigDecimal;

/** Orders aggregated per payment method. */
public interface OrderSalesSummary {

    PaymentMethod getPaymentMethod();

    Long getCount();

    BigDecimal getTotalAmount();
}
