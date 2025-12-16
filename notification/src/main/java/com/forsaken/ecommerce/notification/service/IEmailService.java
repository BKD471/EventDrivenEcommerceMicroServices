package com.forsaken.ecommerce.notification.service;

import com.forsaken.ecommerce.notification.models.PaymentMethod;
import com.forsaken.ecommerce.notification.models.Product;
import jakarta.mail.MessagingException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Contract for sending customer-facing email notifications.
 *
 * <p>
 * This interface defines the business-level email operations exposed by
 * the Notification service. Implementations are responsible for composing
 * and delivering transactional emails such as:
 * </p>
 *
 * <ul>
 *     <li>Payment success notifications</li>
 *     <li>Order confirmation notifications</li>
 * </ul>
 *
 * <p>
 * <b>Design principles:</b>
 * </p>
 * <ul>
 *     <li>This interface is intentionally free of infrastructure concerns
 *         (SMTP, templates, async execution).</li>
 *     <li>It operates purely on domain models and primitives.</li>
 *     <li>Implementations may choose synchronous or asynchronous execution.</li>
 * </ul>
 *
 * <p>
 * <b>Note:</b> Although implementations may use {@code @Async},
 * this interface remains synchronous by contract to keep it framework-agnostic.
 * </p>
 */
public interface IEmailService {

    /**
     * Sends a payment success email to the customer.
     *
     * <p>
     * The email typically contains:
     * </p>
     * <ul>
     *     <li>Payment confirmation details</li>
     *     <li>Invoice attachment (PDF)</li>
     *     <li>Optional invoice download link</li>
     * </ul>
     *
     * @param destinationEmail customer's email address
     * @param customerName     customer's full name
     * @param amount           payment amount
     * @param orderReference   unique order reference
     * @param paymentMethod    method used for payment
     * @param paymentDate      date and time of payment
     */
    void sendPaymentSuccessEmail(
            final String destinationEmail,
            final String customerName,
            final BigDecimal amount,
            final String orderReference,
            final PaymentMethod paymentMethod,
            final LocalDateTime paymentDate
    );

    /**
     * Sends an order confirmation email to the customer.
     *
     * <p>
     * The email typically contains:
     * </p>
     * <ul>
     *     <li>Order reference</li>
     *     <li>Total order amount</li>
     *     <li>List of purchased products</li>
     * </ul>
     *
     * @param destinationEmail customer's email address
     * @param customerName     customer's full name
     * @param amount           total order amount
     * @param orderReference   unique order reference
     * @param productList      list of purchased products
     */
    void sendOrderConfirmationEmail(
            final String destinationEmail,
            final String customerName,
            final BigDecimal amount,
            final String orderReference,
            final List<Product> productList
    );
}
