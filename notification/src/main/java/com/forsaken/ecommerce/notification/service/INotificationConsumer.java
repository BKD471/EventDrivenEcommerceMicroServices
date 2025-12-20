package com.forsaken.ecommerce.notification.service;

import com.forsaken.ecommerce.avro.OrderConfirmation;
import com.forsaken.ecommerce.avro.PaymentConfirmation;
import org.apache.kafka.clients.consumer.ConsumerRecord;

import java.io.IOException;

/**
 * Contract for consuming notification-related Kafka events and their
 * corresponding Dead Letter Queue (DLQ) messages.
 *
 * <p>
 * Implementations of this interface act as the primary entry point for
 * processing notification events published to Kafka topics.
 * These events typically originate from upstream services after
 * successful business operations such as payments or order creation.
 * </p>
 *
 * <p>
 * <b>Primary Responsibilities:</b>
 * </p>
 * <ul>
 *     <li>Consume and process payment success notification events.</li>
 *     <li>Consume and process order confirmation notification events.</li>
 *     <li>Persist notification data for auditing and traceability.</li>
 *     <li>Trigger downstream side effects such as email notifications.</li>
 *     <li>Handle failed messages routed to Dead Letter Topics (DLQ).</li>
 * </ul>
 *
 * <p>
 * <b>Kafka Integration:</b>
 * </p>
 * <ul>
 *     <li>Concrete implementations are expected to use
 *         {@code @KafkaListener} annotations.</li>
 *     <li>Topic names, consumer groups, retry policies, and DLQ configuration
 *         should be externalized via application configuration.</li>
 *     <li>This interface intentionally contains no Kafka annotations and
 *         represents a pure business contract.</li>
 * </ul>
 *
 * <p>
 * <b>DLQ Handling:</b>
 * </p>
 * <ul>
 *     <li>Messages that fail processing after retries may be delivered
 *         to DLQ topics.</li>
 *     <li>DLQ consumers are responsible for capturing and persisting
 *         failed records for later inspection or replay.</li>
 * </ul>
 *
 * <p>
 * <b>Error Handling & Resilience:</b>
 * </p>
 * <ul>
 *     <li>Implementations should handle failures gracefully and ensure
 *         that Kafka listener threads are not terminated unexpectedly.</li>
 *     <li>Exceptions may be logged, routed to DLQ, or persisted based on
 *         system retry and recovery policies.</li>
 * </ul>
 *
 * <p>
 * <b>Thread Safety:</b><br>
 * Implementations must be stateless and thread-safe, as Kafka listeners
 * may process records concurrently depending on container configuration.
 * </p>
 */
public interface INotificationConsumer {

    /**
     * Consumes and processes payment success notification events.
     *
     * <p>
     * These events are emitted after a successful payment transaction.
     * Typical responsibilities include:
     * </p>
     * <ul>
     *     <li>Validating the {@link PaymentConfirmation} payload.</li>
     *     <li>Persisting payment notification details.</li>
     *     <li>Triggering payment success notifications (e.g. email).</li>
     * </ul>
     *
     * @param record the Kafka {@link ConsumerRecord} containing a
     *               {@link PaymentConfirmation} event as its value
     */
    void consumePaymentSuccessNotifications(
            final ConsumerRecord<String, PaymentConfirmation> record
    );

    /**
     * Consumes and processes order confirmation notification events.
     *
     * <p>
     * These events are emitted when an order has been successfully created
     * or confirmed. Implementations typically:
     * </p>
     * <ul>
     *     <li>Extract order and customer information.</li>
     *     <li>Persist order notification details.</li>
     *     <li>Trigger order confirmation notifications.</li>
     * </ul>
     *
     * @param record the Kafka {@link ConsumerRecord} containing an
     *               {@link OrderConfirmation} event as its value
     */
    void consumeOrderConfirmationNotifications(
            final ConsumerRecord<String, OrderConfirmation> record
    );

    /**
     * Consumes payment-related messages from the Dead Letter Topic (DLQ).
     *
     * <p>
     * This method is invoked for {@link PaymentConfirmation} events that could not be
     * processed successfully by the primary consumer after all configured retry
     * attempts have been exhausted.
     * </p>
     *
     * <p>
     * Implementations are expected to handle the failed record in a safe and
     * idempotent manner. Typical responsibilities include:
     * </p>
     * <ul>
     *   <li>Persisting the failed event payload for later inspection or replay</li>
     *   <li>Storing Kafka metadata such as topic, partition, offset, and timestamp</li>
     *   <li>Emitting logs or metrics for monitoring and alerting</li>
     * </ul>
     *
     * <p>
     * This consumer <b>must not</b> throw unchecked exceptions that could cause
     * repeated reprocessing of the same DLQ message unless explicitly desired.
     * </p>
     *
     * @param record the Kafka {@link ConsumerRecord} containing the failed
     *               {@link PaymentConfirmation} event and its associated metadata
     * @throws IOException if an I/O error occurs while persisting or exporting
     *                     the failed message
     */
    void consumePaymentDlqMessages(
            final ConsumerRecord<String, PaymentConfirmation> record
    ) throws IOException;

    /**
     * Consumes order-related messages from the Dead Letter Topic (DLQ).
     *
     * <p>
     * This method handles order confirmation events that failed processing
     * and were redirected to a DLQ.
     * </p>
     *
     * <p>
     * Implementations should store the failed event in durable storage
     * and make it available for manual inspection or replay.
     * </p>
     *
     * @param record the Kafka {@link ConsumerRecord} containing the
     *               failed {@link OrderConfirmation} event
     * @throws IOException if an I/O error occurs while persisting or exporting
     *                     the failed message
     */
    void consumeOrderDlqMessages(
            final ConsumerRecord<String, OrderConfirmation> record
    ) throws IOException;
}
