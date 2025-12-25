package com.forsaken.ecommerce.order.customer;

import com.forsaken.ecommerce.common.exceptions.CustomerNotFoundExceptions;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Service interface for handling customer-related operations.
 * <p>
 * This service provides asynchronous methods for retrieving customer
 * information from external or internal data sources. Implementations of this
 * interface are expected to interact with a persistence layer, a remote service,
 * or a combination of both.
 * </p>
 *
 * <p><b>Asynchronous Execution:</b></p>
 * <ul>
 *     <li>The {@link #getCustomer(String)} method executes asynchronously
 *         using the task executor named {@code appTaskExecutor}.</li>
 *     <li>Calling this method immediately returns a {@link CompletableFuture},
 *         allowing the caller to continue processing without blocking.</li>
 * </ul>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>
 * CompletableFuture<Optional<CustomerResponse>> future = customerService.getCustomer("123");
 *
 * future.thenAccept(optionalCustomer -> {
 *     optionalCustomer.ifPresent(customer -> {
 *         // Process customer data
 *     });
 * });
 * </pre>
 *
 * @author Your Name
 * @see CustomerResponse
 */
public interface ICustomerService {

    /**
     * Retrieves customer details by their unique ID in an asynchronous manner.
     * <p>
     * This method delegates execution to the {@code appTaskExecutor} and returns
     * immediately with a {@link CompletableFuture}. The future completes with:
     * </p>
     *
     * <ul>
     *     <li>An {@link Optional} containing the {@link CustomerResponse}
     *         if the customer is found.</li>
     *     <li>An empty {@link Optional} if the customer does not exist.</li>
     * </ul>
     *
     * <p><b>Error Handling:</b></p>
     * <ul>
     *     <li>If an exception occurs during the async call, the future completes
     *         exceptionally.</li>
     *     <li>Callers can use {@code future.exceptionally(...)} to handle errors.</li>
     * </ul>
     *
     * @param customerId the unique identifier of the customer to retrieve; must not be null
     * @return a {@link CompletableFuture} containing an {@link Optional}
     * with the customer data, or empty if not found
     */

    CompletableFuture<CustomerResponse> getCustomer(final String customerId) throws CustomerNotFoundExceptions;
}
