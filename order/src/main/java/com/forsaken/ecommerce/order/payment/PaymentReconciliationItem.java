package com.forsaken.ecommerce.order.payment;

/** Payment view returned by the payment service's reconciliation endpoint. */
public record PaymentReconciliationItem(Integer id, Integer orderId, String status) {
}
