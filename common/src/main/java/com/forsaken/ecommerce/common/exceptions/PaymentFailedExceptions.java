package com.forsaken.ecommerce.common.exceptions;

import lombok.Getter;

@Getter
public class PaymentFailedExceptions extends Exception {

    private final String methodName;

    public PaymentFailedExceptions(final String message) {
        super(message);
        this.methodName = null;
    }

    public PaymentFailedExceptions(
            final String message,
            final String methodName
    ) {
        super(String.format("%s in %s", message, methodName));
        this.methodName = methodName;
    }

    public PaymentFailedExceptions(
            final String message,
            final String methodName,
            final Throwable cause
    ) {
        super(String.format("%s in %s", message, methodName), cause);
        this.methodName = methodName;
    }
}
