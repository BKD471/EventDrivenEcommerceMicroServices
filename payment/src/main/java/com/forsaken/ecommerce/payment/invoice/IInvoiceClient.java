package com.forsaken.ecommerce.payment.invoice;

import com.forsaken.ecommerce.payment.model.Payment;

/** Client for the notification service's invoice endpoints. */
public interface IInvoiceClient {

    boolean exists(String orderReference);

    /** Asks the notification service to rebuild and email the invoice; a no-op there if it already exists. */
    void regenerate(Payment payment);
}
