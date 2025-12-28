package com.forsaken.ecommerce.order.order.service;

import com.forsaken.ecommerce.avro.OrderConfirmation;

/**
 * Producer interface responsible for publishing order confirmation events.
 * <p>
 * Implementations of this interface typically interact with a messaging
 * infrastructure such as Kafka, RabbitMQ, or any event streaming platform.
 * The event encapsulates essential order-related information and is consumed
 * by downstream services notification service.
 * </p>
 *
 * <p><b>Usage:</b></p>
 * <pre>
 * OrderConfirmation confirmation = ...;
 * orderProducer.sendOrderConfirmation(confirmation);
 * </pre>
 *
 * <p>
 * Implementations must ensure reliable delivery according to the system’s
 * messaging strategy (e.g., retries, DLQ, idempotency, acknowledgment handling).
 * </p>
 *
 * @see OrderConfirmation
 */
public interface IOrderProducer {

    /**
     * Publishes an {@link OrderConfirmation} event to the message broker.
     * <p>
     * The event contains information about the newly created order and is
     * intended for asynchronous processing by other microservices.
     * Typical use cases include sending notifications, updating inventory,
     * or triggering payment workflows.
     * </p>
     *
     * <p><b>Error Handling:</b></p>
     * <ul>
     *     <li>Messaging-related failures (serialization errors, broker downtime, etc.)
     *         should be handled or propagated by the implementation.</li>
     *     <li>Implementations should define retry logic or DLQ behavior as appropriate.</li>
     * </ul>
     *
     * @param orderConfirmation the confirmation event containing order ID, customer info,
     *                          product details, and metadata; must not be null
     */
    void sendOrderConfirmation(final OrderConfirmation orderConfirmation);
}
