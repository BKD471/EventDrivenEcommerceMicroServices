package com.forsaken.ecommerce.order.order.service;

import com.forsaken.ecommerce.common.exceptions.BusinessException;
import com.forsaken.ecommerce.common.exceptions.CustomerNotFoundExceptions;
import com.forsaken.ecommerce.common.exceptions.PaymentFailedExceptions;
import com.forsaken.ecommerce.common.exceptions.ProductNotFoundExceptions;
import com.forsaken.ecommerce.common.responses.PagedResponse;
import com.forsaken.ecommerce.order.configs.general.OrderProperties;
import com.forsaken.ecommerce.order.order.dto.OrderRequest;
import com.forsaken.ecommerce.order.order.dto.OrderResponse;

import java.time.LocalDateTime;
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
     * Retrieves a paginated list of all orders available in the system.
     *
     * <p>
     * This operation supports optional pagination parameters. When {@code page} or
     * {@code size} are {@code null}, the service applies default pagination values
     * configured via {@link OrderProperties}.
     * </p>
     *
     * <p>
     * Pagination semantics:
     * </p>
     * <ul>
     *   <li>Page numbering is <b>1-based</b> from the API consumer’s perspective.</li>
     *   <li>Internally, pagination is translated to a zero-based {@link org.springframework.data.domain.Pageable}.</li>
     *   <li>The result set is ordered by creation time in descending order.</li>
     * </ul>
     *
     * <p>
     * The returned {@link PagedResponse} includes pagination metadata such as
     * total elements, total pages, and whether the current page is the last page.
     * </p>
     *
     * @param page the page number to retrieve (1-based); may be {@code null} to
     *             use the default page number.
     * @param size the number of records per page; may be {@code null} to
     *             use the default page size.
     *
     * @return a {@link PagedResponse} containing {@link OrderResponse} items and
     *         associated pagination metadata.
     */
    PagedResponse<OrderResponse> findAllOrders(
            final Integer page,
            final Integer size
    );

    /**
     * Finds a single order using its unique identifier.
     *
     * @param id the order ID to search for.
     * @return the {@link OrderResponse} representation of the order.
     * @throws jakarta.persistence.EntityNotFoundException if the order does not exist.
     */
    OrderResponse findById(final Integer id);

    /**
     * Retrieves a paginated list of orders associated with a specific customer.
     *
     * <p>
     * The result set may be optionally filtered by an order creation date range.
     * When date parameters are not provided, no date-based filtering is applied.
     * </p>
     *
     * <p>
     * Pagination behavior:
     * </p>
     * <ul>
     *   <li>Pagination parameters {@code page} and {@code size} are optional.</li>
     *   <li>When {@code page} or {@code size} are {@code null}, default values
     *       configured via {@link OrderProperties} are applied.</li>
     *   <li>Page numbering is <b>1-based</b> from the API consumer’s perspective
     *       and translated internally to a zero-based index.</li>
     *   <li>Results are ordered by creation time in descending order.</li>
     * </ul>
     *
     * <p>
     * The returned {@link PagedResponse} includes both the order data and pagination
     * metadata such as total elements, total pages, and whether the current page is
     * the last page.
     * </p>
     *
     * @param customerId the unique identifier of the customer whose orders
     *                   should be retrieved; must not be {@code null} or blank.
     * @param fromDate   the start of the order creation date range filter
     *                   (inclusive); may be {@code null}.
     * @param toDate     the end of the order creation date range filter
     *                   (inclusive); may be {@code null}.
     * @param page       the page number to retrieve (1-based); may be {@code null}
     *                   to use the configured default page number.
     * @param size       the number of records per page; may be {@code null}
     *                   to use the configured default page size.
     *
     * @return a {@link PagedResponse} containing {@link OrderResponse} items
     *         belonging to the specified customer along with pagination metadata.
     */
    PagedResponse<OrderResponse> findAllOrdersByCustomerId(
            final String customerId,
            final LocalDateTime fromDate,
            final LocalDateTime toDate,
            final Integer page,
            final Integer size
    );
}
