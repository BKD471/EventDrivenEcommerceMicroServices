package com.forsaken.ecommerce.payment.invoice;

/** The notification service could not be reached or refused the request. Retryable, then skippable. */
public class InvoiceClientException extends RuntimeException {

    public InvoiceClientException(final String message) {
        super(message);
    }

    public InvoiceClientException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
