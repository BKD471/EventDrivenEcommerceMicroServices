package com.forsaken.ecommerce.product.order;

import java.util.Optional;

/** Looks up order state in the order service. */
public interface IOrderStatusClient {

    /**
     * Returns the saga status of the order, or empty when the order service does not know the reference.
     *
     * @throws OrderStatusLookupException if the order service could not be reached or answered unexpectedly
     */
    Optional<String> findSagaStatus(String orderReference);
}
