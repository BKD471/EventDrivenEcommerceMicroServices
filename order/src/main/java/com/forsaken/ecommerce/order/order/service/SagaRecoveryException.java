package com.forsaken.ecommerce.order.order.service;

/** Raised when a saga recovery attempt for one order did not complete; the order stays recoverable. */
public class SagaRecoveryException extends RuntimeException {

    public SagaRecoveryException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
