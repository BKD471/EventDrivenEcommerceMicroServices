package com.forsaken.ecommerce.order.payment;

import com.forsaken.ecommerce.common.exceptions.PaymentFailedExceptions;

import java.util.concurrent.CompletableFuture;

/**
 * Service interface for handling payment processing operations.
 * <p>
 * Implementations of this interface are responsible for validating payment
 * requests, interacting with external or internal payment gateways, updating
 * payment status, and returning a unique identifier representing the processed
 * payment transaction. The method executes asynchronously to avoid blocking
 * caller threads.
 * </p>
 *
 * <p><b>Asynchronous Execution:</b></p>
 * <ul>
 *     <li>The {@link #pay(PaymentRequest)} method returns immediately with a
 *         {@link CompletableFuture}, allowing the caller to continue without waiting.</li>
 *     <li>The future completes with the generated payment ID or completes exceptionally
 *         if a processing error occurs.</li>
 * </ul>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>
 * CompletableFuture<Integer> future = paymentService.pay(paymentRequest);
 *
 * future.thenAccept(paymentId -> {
 *     // handle successful payment
 * }).exceptionally(ex -> {
 *     // handle errors
 *     return null;
 * });
 * </pre>
 *
 * @see PaymentRequest
 */
public interface IPaymentService {

    /**
     * Processes a payment request asynchronously.
     * <p>
     * Validates the incoming {@link PaymentRequest}, executes payment logic
     * (such as contacting a payment provider or updating internal state), and
     * returns a {@link CompletableFuture} that resolves to the unique payment ID
     * generated after successful processing.
     * </p>
     *
     * <p><b>Error Handling:</b></p>
     * <ul>
     *     <li>The returned {@code CompletableFuture} completes exceptionally if:
     *         <ul>
     *             <li>validation of the payment request fails,</li>
     *             <li>communication with a payment provider fails,</li>
     *             <li>a business rule is violated (e.g., invalid amount), or</li>
     *             <li>any unexpected runtime error occurs.</li>
     *         </ul>
     *     </li>
     * </ul>
     *
     * @param request the incoming payment request containing order ID, amount,
     *                payment method, and other required metadata; must not be null
     * @return a {@link CompletableFuture} resolving to the unique payment ID upon success
     */
    CompletableFuture<Integer> pay(final PaymentRequest request) throws PaymentFailedExceptions;
}
