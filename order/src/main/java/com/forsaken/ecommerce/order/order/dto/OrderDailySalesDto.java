package com.forsaken.ecommerce.order.order.dto;

import com.forsaken.ecommerce.order.order.model.OrderDailySales;
import com.forsaken.ecommerce.order.order.model.PaymentMethod;

import java.math.BigDecimal;
import java.time.LocalDate;

public record OrderDailySalesDto(
        LocalDate date, PaymentMethod paymentMethod, long orderCount, BigDecimal totalAmount) {

    public static OrderDailySalesDto from(final OrderDailySales sales) {
        return new OrderDailySalesDto(sales.getSalesDate(), sales.getPaymentMethod(),
                sales.getOrderCount(), sales.getTotalAmount());
    }
}
