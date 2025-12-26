package com.forsaken.ecommerce.order.payment;

import com.forsaken.ecommerce.common.exceptions.PaymentFailedExceptions;

/**
 * Service interface for handling payment processing operations.
 *
 * <p>
 * Implementations of this interface are responsible for validating payment
 * requests, interacting with external or internal payment gateways, and
 * returning a unique identifier representing the processed payment transaction.
 * </p>
 *
 * <p>
 * <b>Synchronous Execution:</b>
 * </p>
 * <ul>
 *     <li>The {@link #pay(PaymentRequest)} method executes synchronously.</li>
 *     <li>The caller is blocked until the payment operation completes.</li>
 *     <li>If payment processing fails, a {@link PaymentFailedExceptions} is thrown.</li>
 * </ul>
 *
 * <p>
 * This synchronous design is intentional to ensure <strong>transactional
 * consistency</strong>. When invoked inside a {@code @Transactional} boundary,
 * any payment failure will correctly trigger a rollback of the surrounding
 * transaction (e.g., order creation).
 * </p>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>
 * try {
 *     Integer paymentId = paymentService.pay(paymentRequest);
 *     // handle successful payment
 * } catch (PaymentFailedExceptions ex) {
 *     // handle payment failure
 * }
 * </pre>
 *
 * @see PaymentRequest
 * @see PaymentFailedExceptions
 */
public interface IPaymentService {

    /**
     * Processes a payment request synchronously.
     *
     * <p>
     * Validates the incoming {@link PaymentRequest}, executes payment logic
     * (such as contacting a payment provider), and returns the unique payment ID
     * generated after successful processing.
     * </p>
     *
     * <p><b>Error Handling:</b></p>
     * <ul>
     *     <li>This method throws {@link PaymentFailedExceptions} if:
     *         <ul>
     *             <li>the payment request is invalid,</li>
     *             <li>communication with a payment provider fails,</li>
     *             <li>a business rule is violated (e.g., insufficient funds), or</li>
     *             <li>an unexpected runtime error occurs.</li>
     *         </ul>
     *     </li>
     * </ul>
     *
     * @param request the incoming payment request containing order ID, amount,
     *                payment method, and other required metadata; must not be null
     * @return the unique payment ID generated upon successful payment
     * @throws PaymentFailedExceptions if payment processing fails for any reason
     */
    Integer pay(final PaymentRequest request) throws PaymentFailedExceptions;
}
