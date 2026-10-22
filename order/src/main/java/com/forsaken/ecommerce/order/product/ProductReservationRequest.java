package com.forsaken.ecommerce.order.product;

import java.util.List;

public record ProductReservationRequest(String orderReference, List<PurchaseRequest> products) {
}
