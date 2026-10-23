package com.forsaken.ecommerce.product.order;

/** The order status could not be determined; callers must not assume the order is missing. */
public class OrderStatusLookupException extends RuntimeException {

    public OrderStatusLookupException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public OrderStatusLookupException(final String message) {
        super(message);
    }
}
