package com.forsaken.ecommerce.order.product;

import com.forsaken.ecommerce.common.exceptions.BusinessException;
import com.forsaken.ecommerce.common.exceptions.ProductNotFoundExceptions;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Service interface for handling product purchase operations.
 * <p>
 * This service provides asynchronous execution for processing product purchase
 * requests. Implementations of this interface are expected to handle validation,
 * inventory checks, pricing rules, payment triggers, or communication with
 * other microservices as needed.
 * </p>
 *
 * <p><b>Asynchronous Execution:</b></p>
 * <ul>
 *     <li>The {@link #purchaseProducts(List)} method runs asynchronously using
 *         the {@code appTaskExecutor}.</li>
 *     <li>The method returns immediately with a {@link CompletableFuture},
 *         allowing non-blocking request handling.</li>
 * </ul>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>
 * List<PurchaseRequest> requests = List.of(
 *     new PurchaseRequest("P101", 2),
 *     new PurchaseRequest("P202", 1)
 * );
 *
 * CompletableFuture<List<PurchaseResponse>> future =
 *         productService.purchaseProducts(requests);
 *
 * future.thenAccept(responses -> {
 *     responses.forEach(System.out::println);
 * });
 * </pre>
 *
 * <p>Errors thrown during processing will cause the {@code CompletableFuture}
 * to complete exceptionally.</p>
 *
 * @see PurchaseRequest
 * @see PurchaseResponse
 * @see BusinessException
 */
public interface IProductService {

    /**
     * Processes a batch of product purchase requests asynchronously.
     * <p>
     * This method validates the incoming purchase requests, checks product
     * availability, calculates pricing if necessary, and returns a list of
     * {@link PurchaseResponse} objects representing the outcome of each
     * purchase operation.
     * </p>
     *
     * <p><b>Behavior:</b></p>
     * <ul>
     *     <li>Runs asynchronously using the defined executor {@code appTaskExecutor}.</li>
     *     <li>
     *         Completes successfully with a list of {@link PurchaseResponse}
     *         objects when processing is successful.
     *     </li>
     *     <li>
     *         Completes exceptionally if any business rule fails or a system error occurs.
     *     </li>
     * </ul>
     *
     * <p><b>Error Handling:</b></p>
     * <ul>
     *     <li>Throws {@link BusinessException} for domain-specific failures such as
     *         insufficient stock, invalid request data, or pricing rule violations.</li>
     *     <li>Runtime exceptions will also cause the returned {@code CompletableFuture}
     *         to complete exceptionally.</li>
     * </ul>
     *
     * @param requestBody a list of purchase requests; must not be null or empty
     * @return a {@link CompletableFuture} that resolves to a list of purchase responses
     * @throws BusinessException if any business validation or rule check fails
     */
    CompletableFuture<List<PurchaseResponse>> purchaseProducts(final List<PurchaseRequest> requestBody) throws BusinessException, ProductNotFoundExceptions;
}
