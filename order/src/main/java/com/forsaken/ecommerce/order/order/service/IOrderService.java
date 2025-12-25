package com.forsaken.ecommerce.order.order.service;

import com.forsaken.ecommerce.common.exceptions.BusinessException;
import com.forsaken.ecommerce.common.exceptions.CustomerNotFoundExceptions;
import com.forsaken.ecommerce.common.exceptions.PaymentFailedExceptions;
import com.forsaken.ecommerce.common.exceptions.ProductNotFoundExceptions;
import com.forsaken.ecommerce.order.order.dto.OrderRequest;
import com.forsaken.ecommerce.order.order.dto.OrderResponse;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * Service interface for managing Orders within the system.
 * <p>
 * Provides operations for creating new orders, retrieving orders by
 * various filters, and fetching detailed information for a specific order.
 * This service acts as the core business layer for order operations.
 */
public interface IOrderService {

    /**
     * Creates a new order based on the provided {@link OrderRequest}.
     * <p>
     * This operation is responsible for validating the incoming request,
     * verifying customer existence, checking product availability, calculating
     * order totals, and persisting the order. Implementations may also trigger
     * asynchronous workflows such as event publishing (e.g., sending order
     * confirmation, updating inventory, initiating payment).
     * </p>
     *
     * <p><b>Behavior:</b></p>
     * <ul>
     *     <li>Validates customer and product details.</li>
     *     <li>Performs business-rule checks (inventory, pricing, quantity, etc.).</li>
     *     <li>Persists the order and returns its unique identifier.</li>
     *     <li>May publish domain events asynchronously.</li>
     * </ul>
     *
     * <p><b>Error Handling:</b></p>
     * <ul>
     *     <li>{@link CustomerNotFoundExceptions} –
     *         thrown when the referenced customer does not exist.</li>
     *     <li>{@link BusinessException} –
     *         thrown when one or more business rules are violated
     *         (invalid product, insufficient stock, invalid quantity, etc.).</li>
     *     <li>{@link ExecutionException} –
     *         thrown if an asynchronous operation (such as event publishing)
     *         fails during processing.</li>
     *     <li>{@link InterruptedException} –
     *         thrown if the executing thread is interrupted while waiting on
     *         asynchronous completion.</li>
     * </ul>
     *
     * @param request the order creation payload containing customer ID,
     *                product line items, payment details, and any additional metadata;
     *                must not be null.
     * @return the unique identifier of the newly created order.
     * @throws CustomerNotFoundExceptions if the associated customer cannot be found.
     * @throws BusinessException          if validation or business-rule checks fail.
     * @throws ExecutionException         if asynchronous event processing fails.
     * @throws InterruptedException       if the thread is interrupted during execution.
     */
    Integer createOrder(final OrderRequest request) throws ExecutionException, InterruptedException,
            CustomerNotFoundExceptions, BusinessException, PaymentFailedExceptions, ProductNotFoundExceptions;

    /**
     * Retrieves all orders available in the system.
     *
     * @return a list of {@link OrderResponse} containing summary or detailed
     * information for each order.
     */
    List<OrderResponse> findAllOrders();

    /**
     * Finds a single order using its unique identifier.
     *
     * @param id the order ID to search for.
     * @return the {@link OrderResponse} representation of the order.
     * @throws jakarta.persistence.EntityNotFoundException if the order does not exist.
     */
    OrderResponse findById(final Integer id);

    /**
     * Retrieves all orders associated with a specific customer.
     * Optional date filters may be applied to restrict results to a time range.
     *
     * @param customerId the unique identifier of the customer whose orders
     *                   should be fetched.
     * @param fromDate   (optional) start of the creation date range filter;
     *                   may be {@code null}.
     * @param toDate     (optional) end of the creation date range filter;
     *                   may be {@code null}.
     * @return a list of {@link OrderResponse} belonging to the specified customer,
     * optionally filtered by date.
     */
    List<OrderResponse> findAllOrdersByCustomerId(
            final String customerId,
            final LocalDateTime fromDate,
            final LocalDateTime toDate
    );
}
