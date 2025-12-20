package com.forsaken.ecommerce.notification.service;

import com.forsaken.ecommerce.avro.OrderConfirmation;
import com.forsaken.ecommerce.avro.PaymentConfirmation;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.support.Acknowledgment;


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
     * Consumes payment confirmation events from the Kafka topic.
     *
     * <p>
     * Payment confirmation events are published after a payment has been
     * successfully completed and validated by the payment service.
     * </p>
     *
     * <p>
     * Typical responsibilities of this consumer include:
     * </p>
     * <ul>
     *     <li>Extracting payment and order reference details</li>
     *     <li>Persisting payment notification metadata</li>
     *     <li>Triggering downstream actions such as user notifications</li>
     * </ul>
     *
     * <p>
     * <b>Null record handling:</b><br>
     * In rare scenarios (for example Kafka tombstone messages, topic compaction,
     * or upstream producer issues), the {@link ConsumerRecord#value()} may be
     * {@code null}.
     * </p>
     *
     * <p>
     * A {@code null} value does <b>not</b> represent a valid
     * {@link PaymentConfirmation} domain event and is considered
     * <b>non-recoverable</b>. Retrying or forwarding such records to a
     * Dead Letter Queue (DLQ) provides no operational benefit.
     * </p>
     *
     * <p>
     * When a {@code null} {@link PaymentConfirmation} is encountered:
     * </p>
     * <ul>
     *     <li>The condition is logged for observability and diagnostics</li>
     *     <li>The Kafka offset is acknowledged immediately</li>
     *     <li>Further processing is skipped to prevent infinite retry loops</li>
     * </ul>
     *
     * <p>
     * This approach ensures:
     * </p>
     * <ul>
     *     <li>Stable and predictable consumer progress</li>
     *     <li>Clean DLQ usage limited to genuine processing failures</li>
     *     <li>Resilience against malformed or control-plane Kafka records</li>
     * </ul>
     *
     * @param record         the Kafka {@link ConsumerRecord} containing the
     *                       {@link PaymentConfirmation} event
     * @param acknowledgment manual acknowledgment handle used to commit the offset
     */
    void consumePaymentSuccessNotifications(
            final ConsumerRecord<String, PaymentConfirmation> record,
            final Acknowledgment acknowledgment
    );

    /**
     * Consumes and processes order confirmation notification events from Kafka.
     *
     * <p>
     * Order confirmation events are published after an order has been
     * successfully created and validated in the order service.
     * </p>
     *
     * <p>
     * Typical responsibilities of this consumer include:
     * </p>
     * <ul>
     *     <li>Extracting order and customer details from the event</li>
     *     <li>Persisting order notification metadata</li>
     *     <li>Triggering downstream notifications such as order confirmation emails</li>
     * </ul>
     *
     * <p>
     * <b>Null record handling:</b><br>
     * In exceptional scenarios (for example Kafka tombstone messages,
     * topic compaction, or upstream producer issues), the
     * {@link ConsumerRecord#value()} may be {@code null}.
     * </p>
     *
     * <p>
     * A {@code null} value does <b>not</b> represent a valid
     * {@link OrderConfirmation} domain event and is considered
     * <b>non-recoverable</b>. Retrying or forwarding such records to a
     * Dead Letter Queue (DLQ) provides no operational value.
     * </p>
     *
     * <p>
     * When a {@code null} {@link OrderConfirmation} is encountered:
     * </p>
     * <ul>
     *     <li>The condition is logged for observability and diagnostics</li>
     *     <li>The Kafka offset is acknowledged immediately</li>
     *     <li>Further processing is skipped to avoid infinite retry loops</li>
     * </ul>
     *
     * <p>
     * This approach ensures:
     * </p>
     * <ul>
     *     <li>Stable and predictable consumer progress</li>
     *     <li>DLQ usage is reserved for genuine processing failures</li>
     *     <li>Resilience against malformed or control-plane Kafka records</li>
     * </ul>
     *
     * @param record         the Kafka {@link ConsumerRecord} containing the
     *                       {@link OrderConfirmation} event
     * @param acknowledgment manual acknowledgment handle used to commit the offset
     */
    void consumeOrderConfirmationNotifications(
            final ConsumerRecord<String, OrderConfirmation> record,
            final Acknowledgment acknowledgment
    );

    /**
     * Consumes payment-related messages from the Kafka Dead Letter Topic (DLQ).
     *
     * <p>
     * This listener is invoked for {@link PaymentConfirmation} events that have
     * permanently failed processing in the primary consumer after all configured
     * retry attempts have been exhausted.
     * </p>
     *
     * <p>
     * The primary responsibility of this method is to persist the failed Kafka
     * record to durable storage (Amazon S3) for later inspection, auditing, or
     * manual replay. Persisted data typically includes:
     * </p>
     * <ul>
     *     <li>The original event payload</li>
     *     <li>Kafka metadata such as topic, partition, offset, and timestamp</li>
     * </ul>
     *
     * <p>
     * <b>Acknowledgment strategy:</b><br>
     * The Kafka offset is <b>manually acknowledged only after successful persistence
     * to S3</b>. If persistence fails, the offset is deliberately <b>not acknowledged</b>
     * to prevent silent loss of DLQ records.
     * </p>
     *
     * <p>
     * <b>Failure handling:</b><br>
     * If persistence to S3 fails (for example due to transient infrastructure issues),
     * the exception is caught and logged and is <b>not rethrown</b>. The listener does
     * not propagate the exception to the Kafka container, avoiding crash loops while
     * preserving visibility into the failure.
     * </p>
     *
     * <p>
     * <b>Design rationale:</b><br>
     * DLQ records represent already-failed messages. Automatically retrying DLQ
     * consumption often provides limited value and can lead to repeated failures.
     * This implementation prioritizes operational stability and observability.
     * </p>
     *
     * @param record the Kafka {@link ConsumerRecord} containing the failed
     *               {@link PaymentConfirmation} event
     * @param acknowledgment manual acknowledgment handle used to commit the offset
     *                       after successful persistence
     */
    void consumePaymentDlqMessages(
            final ConsumerRecord<String, PaymentConfirmation> record,
            final Acknowledgment acknowledgment
    );

    /**
     * Consumes order-related messages from the Kafka Dead Letter Topic (DLQ).
     *
     * <p>
     * This listener is invoked for {@link OrderConfirmation} events that have
     * permanently failed processing in the primary order consumer after all
     * configured retry attempts have been exhausted.
     * </p>
     *
     * <p>
     * The primary responsibility of this method is to persist the failed Kafka
     * record to durable storage (Amazon S3) so that it can be:
     * </p>
     * <ul>
     *     <li>Inspected for debugging and root-cause analysis</li>
     *     <li>Replayed or reprocessed manually at a later time</li>
     *     <li>Audited for compliance or operational visibility</li>
     * </ul>
     *
     * <p>
     * <b>Acknowledgment strategy:</b><br>
     * The Kafka offset is <b>manually acknowledged only after successful persistence
     * to S3</b>. If persistence fails, the offset is deliberately <b>not acknowledged</b>
     * to avoid silently committing the DLQ record.
     * </p>
     *
     * <p>
     * <b>Failure handling:</b><br>
     * If persistence to S3 fails (for example due to transient infrastructure issues),
     * the exception is caught and logged and is <b>not rethrown</b>. The listener does
     * not propagate the exception to the Kafka container, which prevents consumer
     * crash loops while ensuring the failure is visible in logs and monitoring.
     * </p>
     *
     * <p>
     * <b>Design rationale:</b><br>
     * DLQ records represent messages that have already failed normal processing.
     * Retrying DLQ consumption automatically often provides limited value and can
     * lead to repeated failures. This implementation prioritizes operational
     * stability and observability over aggressive retry behavior.
     * </p>
     *
     * @param record the Kafka {@link ConsumerRecord} containing the failed
     *               {@link OrderConfirmation} event
     * @param acknowledgment manual acknowledgment handle used to commit the offset
     *                       after successful persistence
     */
    void consumeOrderDlqMessages(
            final ConsumerRecord<String, OrderConfirmation> record,
            final Acknowledgment acknowledgment
    );
}
