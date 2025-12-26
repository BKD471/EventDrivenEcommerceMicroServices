package com.forsaken.ecommerce.common.exceptions;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class ProductServiceException extends Exception {

    private final String methodName;

    public ProductServiceException(final String message) {
        super(message);
        this.methodName = null;
    }

    public ProductServiceException(
            final String message,
            final String methodName
    ) {
        super(String.format("%s in %s", message, methodName));
        this.methodName = methodName;
    }

    public ProductServiceException(
            final String message,
            final String methodName,
            final Throwable cause
    ) {
        super(String.format("%s in %s", message, methodName), cause);
        this.methodName = methodName;
    }
}
