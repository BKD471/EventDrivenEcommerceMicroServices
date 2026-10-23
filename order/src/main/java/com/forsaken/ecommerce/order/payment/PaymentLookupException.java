package com.forsaken.ecommerce.order.payment;

/** Thrown when the payment service cannot answer a read-only lookup; the caller must not draw conclusions. */
public class PaymentLookupException extends RuntimeException {

    public PaymentLookupException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
