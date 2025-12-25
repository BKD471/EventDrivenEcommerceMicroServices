package com.forsaken.ecommerce.order.order.controller;

import com.forsaken.ecommerce.common.exceptions.BusinessException;
import com.forsaken.ecommerce.common.exceptions.CustomerNotFoundExceptions;
import com.forsaken.ecommerce.common.exceptions.PaymentFailedExceptions;
import com.forsaken.ecommerce.common.exceptions.ProductNotFoundExceptions;
import com.forsaken.ecommerce.common.responses.ApiResponse;
import com.forsaken.ecommerce.order.order.dto.OrderRequest;
import com.forsaken.ecommerce.order.order.dto.OrderResponse;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;
import java.util.List;
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
            @RequestBody @Valid final OrderRequest request
    ) throws ExecutionException, InterruptedException, CustomerNotFoundExceptions,
            BusinessException, PaymentFailedExceptions, ProductNotFoundExceptions;

    /**
     * Retrieves all orders stored in the system.
     *
     * @return {@link ResponseEntity} containing a list of {@link OrderResponse}
     * wrapped in {@link ApiResponse}
     */
    @GetMapping
    ResponseEntity<ApiResponse<List<OrderResponse>>> findAll();

    /**
     * Retrieves all orders associated with a specific customer, optionally
     * filtered by a date range.
     *
     * @param customerId the unique ID of the customer whose orders are being retrieved
     * @param fromDate   optional filter for the starting date/time (inclusive)
     * @param toDate     optional filter for the ending date/time (inclusive)
     * @return {@link ResponseEntity} containing a list of matching {@link OrderResponse}
     * wrapped in {@link ApiResponse}
     */
    @GetMapping("/{customerId}")
    ResponseEntity<ApiResponse<List<OrderResponse>>> findAllOrdersByCustomerId(
            @PathVariable("customerId") final String customerId,

            @RequestParam(value = "fromDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) final LocalDateTime fromDate,

            @RequestParam(value = "toDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) final LocalDateTime toDate
    );

    /**
     * Retrieves a single order by its unique identifier.
     *
     * @param orderId the ID of the order to retrieve
     * @return {@link ResponseEntity} containing the order details wrapped in {@link ApiResponse}
     */
    @GetMapping("/{order-id}")
    ResponseEntity<ApiResponse<OrderResponse>> findById(
            @PathVariable("order-id") final Integer orderId
    );
}
