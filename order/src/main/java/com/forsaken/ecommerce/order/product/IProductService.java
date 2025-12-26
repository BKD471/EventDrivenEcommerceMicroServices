package com.forsaken.ecommerce.order.product;

import com.forsaken.ecommerce.common.exceptions.BusinessException;
import com.forsaken.ecommerce.common.exceptions.ProductNotFoundExceptions;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

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
     * Purchases products asynchronously.
     *
     * <p>Exceptions are propagated via the returned {@link CompletableFuture}:
     * <ul>
     *   <li>{@link BusinessException} – if business validation fails</li>
     *   <li>{@link ProductNotFoundExceptions} – if a referenced product does not exist</li>
     * </ul>
     *
     * Callers must handle failures by:
     * <ul>
     *   <li>calling {@code future.join()} and catching {@link CompletionException}</li>
     *   <li>or using {@code exceptionally()}, {@code handle()}, etc.</li>
     * </ul>
     */
    CompletableFuture<List<PurchaseResponse>> purchaseProducts(final List<PurchaseRequest> requestBody);
}
