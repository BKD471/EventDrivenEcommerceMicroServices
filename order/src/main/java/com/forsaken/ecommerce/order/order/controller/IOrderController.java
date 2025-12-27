package com.forsaken.ecommerce.order.order.controller;

import com.forsaken.ecommerce.common.exceptions.BusinessException;
import com.forsaken.ecommerce.common.exceptions.CustomerNotFoundExceptions;
import com.forsaken.ecommerce.common.exceptions.PaymentFailedExceptions;
import com.forsaken.ecommerce.common.exceptions.ProductNotFoundExceptions;
import com.forsaken.ecommerce.common.responses.ApiResponse;
import com.forsaken.ecommerce.common.responses.PagedResponse;
import com.forsaken.ecommerce.order.order.dto.OrderRequest;
import com.forsaken.ecommerce.order.order.dto.OrderResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutionException;

/**
 * REST controller interface for managing Orders within the system.
 * <p>
 * Exposes endpoints for creating new orders, retrieving all existing orders,
 * fetching orders for a specific customer, and retrieving an individual order
 * by its unique identifier.
 * </p>
 *
 * <p>
 * Implementations of this interface should be annotated with
 * {@code @RestController} and responsible for delegating business logic to the
 * appropriate service layer.
 * </p>
 */
@RequestMapping("/api/v1/orders")
public interface IOrderController {

    /**
     * Creates a new order based on the provided request payload.
     * <p>
     * Performs validation on the input request, delegates order creation logic
     * to the service layer, and returns the generated order ID wrapped inside
     * an {@link ApiResponse}. The response is encapsulated in a
     * {@link ResponseEntity} with an appropriate HTTP status.
     * </p>
     *
     * <p><b>Error Handling:</b></p>
     * <ul>
     *     <li>{@link CustomerNotFoundExceptions} – thrown when the referenced customer does not exist.</li>
     *     <li>{@link BusinessException} – thrown when business rules such as stock availability,
     *         pricing validation, or product constraints are violated.</li>
     *     <li>{@link ExecutionException} – thrown if asynchronous operations
     *         (e.g., event publishing during order creation) fail.</li>
     *     <li>{@link InterruptedException} – thrown if the thread is interrupted
     *         during asynchronous processing.</li>
     * </ul>
     *
     * @param request the order creation payload containing customer ID, product line items,
     *                and payment/metadata fields; must be valid and non-null.
     *
     * @return a {@link ResponseEntity} containing an {@link ApiResponse} wrapper
     *         around the newly created order ID.
     *
     * @throws ExecutionException         if async processing of order creation fails
     * @throws InterruptedException       if the current thread is interrupted
     * @throws CustomerNotFoundExceptions if the referenced customer does not exist
     * @throws BusinessException          if any business rule validation fails
     */
    @PostMapping
    ResponseEntity<ApiResponse<Integer>> createOrder(
            @RequestBody @NotNull @Valid final OrderRequest request
    ) throws ExecutionException, InterruptedException, CustomerNotFoundExceptions,
            BusinessException, PaymentFailedExceptions, ProductNotFoundExceptions;

    /**
     * Retrieves a paginated list of all orders stored in the system.
     *
     * <p>
     * This endpoint supports optional pagination parameters. When pagination
     * parameters are not provided, server-side default values are applied.
     * </p>
     *
     * <p>
     * Request parameters:
     * </p>
     * <ul>
     *   <li>{@code page} – optional page number to retrieve (1-based).</li>
     *   <li>{@code size} – optional number of records per page.</li>
     * </ul>
     *
     * <p>
     * Pagination behavior:
     * </p>
     * <ul>
     *   <li>Page numbering is <b>1-based</b> from the client’s perspective.</li>
     *   <li>If {@code page} or {@code size} are omitted, configured default values
     *       are applied.</li>
     *   <li>Results are ordered by creation time in descending order.</li>
     * </ul>
     *
     * <p>
     * The response body is wrapped in {@link ApiResponse} and contains a
     * {@link PagedResponse} with both order data and pagination metadata such as
     * total elements, total pages, and last-page indicator.
     * </p>
     *
     * @param page optional page number to retrieve (1-based).
     * @param size optional number of records per page.
     *
     * @return a {@link ResponseEntity} containing an {@link ApiResponse} that wraps
     *         a {@link PagedResponse} of {@link OrderResponse} objects.
     */
    @GetMapping
    ResponseEntity<ApiResponse<PagedResponse<OrderResponse>>> findAll(
            @RequestParam(name = "page", required = false) final Integer page,
            @RequestParam(name = "size", required = false) final Integer size
    );

    /**
     * Retrieves a paginated list of orders associated with a specific customer.
     *
     * <p>
     * This endpoint supports optional filtering by order creation date as well as
     * optional pagination parameters. When pagination parameters are not provided,
     * default values configured in the application are applied.
     * </p>
     *
     * <p>
     * Request parameters:
     * </p>
     * <ul>
     *   <li>{@code customerId} – identifies the customer whose orders are requested.</li>
     *   <li>{@code fromDate} – optional start of the creation date range (inclusive).</li>
     *   <li>{@code toDate} – optional end of the creation date range (inclusive).</li>
     *   <li>{@code page} – optional page number (1-based).</li>
     *   <li>{@code size} – optional number of records per page.</li>
     * </ul>
     *
     * <p>
     * Pagination behavior:
     * </p>
     * <ul>
     *   <li>Page numbering is <b>1-based</b> from the client’s perspective.</li>
     *   <li>If {@code page} or {@code size} are omitted, server-side default values
     *       are applied.</li>
     *   <li>Results are ordered by creation time in descending order.</li>
     * </ul>
     *
     * <p>
     * The response body is wrapped in {@link ApiResponse} and contains a
     * {@link PagedResponse} with both the order data and pagination metadata.
     * </p>
     *
     * @param customerId the unique identifier of the customer whose orders
     *                   should be retrieved; must not be blank.
     * @param fromDate   optional start of the order creation date range filter
     *                   (inclusive).
     * @param toDate     optional end of the order creation date range filter
     *                   (inclusive).
     * @param page       optional page number to retrieve (1-based).
     * @param size       optional number of records per page.
     *
     * @return a {@link ResponseEntity} containing an {@link ApiResponse} that wraps
     *         a {@link PagedResponse} of {@link OrderResponse} objects.
     */
    @GetMapping("/order/{customerId}")
    ResponseEntity<ApiResponse<PagedResponse<OrderResponse>>> findAllOrdersByCustomerId(
            @PathVariable("customerId") @NotBlank final String customerId,

            @RequestParam(value = "fromDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) final LocalDateTime fromDate,

            @RequestParam(value = "toDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) final LocalDateTime toDate,

            @RequestParam(name = "page", required = false) final Integer page,
            @RequestParam(name = "size", required = false) final Integer size
    );

    /**
     * Retrieves a single order by its unique identifier.
     *
     * @param orderId the ID of the order to retrieve
     * @return {@link ResponseEntity} containing the order details wrapped in {@link ApiResponse}
     */
    @GetMapping("/id/{order-id}")
    ResponseEntity<ApiResponse<OrderResponse>> findById(
            @PathVariable("order-id") @Positive final Integer orderId
    );
}
