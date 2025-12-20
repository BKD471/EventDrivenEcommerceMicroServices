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
     * <b>Null record handling:</b><br>
     * In rare scenarios (for example, Kafka tombstone messages, compaction,
     * or upstream producer bugs), the {@link ConsumerRecord#value()} may be
     * {@code null}.
     * </p>
     *
     * <p>
     * Such records are considered <b>non-recoverable</b> and do not represent
     * a valid business event. Retrying or sending them to a Dead Letter Queue
     * would not add any value.
     * </p>
     *
     * <p>
     * Therefore, when a {@code null} {@link PaymentConfirmation} is encountered:
     * </p>
     * <ul>
     *     <li>The record is logged for visibility</li>
     *     <li>The offset is acknowledged immediately</li>
     *     <li>Processing is skipped to prevent infinite retries</li>
     * </ul>
     *
     * <p>
     * This behavior ensures:
     * </p>
     * <ul>
     *     <li>Consumer progress is not blocked</li>
     *     <li>No unnecessary DLQ pollution</li>
     *     <li>System stability in the presence of malformed or tombstone records</li>
     * </ul>
     *
     * @param record         the Kafka {@link ConsumerRecord} containing the payment event
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
     * In exceptional scenarios (for example, Kafka tombstone messages,
     * topic compaction, or upstream producer issues), the
     * {@link ConsumerRecord#value()} may be {@code null}.
     * </p>
     *
     * <p>
     * Such records are treated as <b>non-recoverable</b> and do not represent
     * a valid {@link OrderConfirmation} event. Retrying or forwarding them
     * to a Dead Letter Queue would provide no value.
     * </p>
     *
     * <p>
     * When a {@code null} {@link OrderConfirmation} is encountered:
     * </p>
     * <ul>
     *     <li>The occurrence is logged for observability</li>
     *     <li>The offset is acknowledged immediately</li>
     *     <li>Processing is skipped to avoid infinite retry loops</li>
     * </ul>
     *
     * <p>
     * This approach ensures:
     * </p>
     * <ul>
     *     <li>Stable consumer progress</li>
     *     <li>Clean DLQ usage limited to genuine processing failures</li>
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
     * The primary responsibility of this method is to safely persist the failed
     * Kafka record to durable storage (Amazon S3) for later inspection, auditing,
     * or manual replay. The persisted data typically includes:
     * </p>
     * <ul>
     *     <li>The original event payload</li>
     *     <li>Kafka metadata such as topic, partition, offset, and timestamp</li>
     * </ul>
     *
     * <p>
     * <b>Acknowledgment strategy:</b><br>
     * Offsets are typically acknowledged according to the configured Kafka listener
     * container strategy after the listener method completes. In the current
     * reference implementation, this completion occurs even if S3 persistence fails,
     * which means the offset may still be acknowledged despite a persistence error.
     * </p>
     *
     * <p>
     * <b>Failure handling:</b><br>
     * If persistence to S3 fails (for example due to transient infrastructure issues),
     * the exception is caught, logged, and <b>not rethrown</b>. Because the exception
     * is swallowed and not propagated to the Kafka container, the listener will
     * generally treat the record as successfully processed and may acknowledge the
     * offset, preventing automatic re-delivery. Implementations that require Kafka
     * retries must instead rethrow the exception so that the container can apply
     * its configured retry and error-handling mechanisms.
     * </p>
     *
     * <p>
     * This design ensures:
     * </p>
     * <ul>
     *     <li>At-least-once delivery semantics for DLQ records</li>
     *     <li>No silent loss of failed business events</li>
     *     <li>Safe retry behavior without crashing the listener container</li>
     * </ul>
     *
     * @param record         the Kafka {@link ConsumerRecord} containing the failed
     *                       {@link PaymentConfirmation} event and its associated metadata
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
     * The main responsibility of this method is to reliably persist the failed
     * Kafka record to durable storage (Amazon S3) so that it can be:
     * </p>
     * <ul>
     *     <li>Inspected for debugging and root-cause analysis</li>
     *     <li>Replayed or reprocessed manually at a later time</li>
     *     <li>Audited for compliance or operational visibility</li>
     * </ul>
     *
     * <p>
     * <b>Acknowledgment strategy:</b><br>
     * The Kafka offset is acknowledged <b>only after</b> the DLQ record has been
     * successfully persisted to S3. This ensures that order-related DLQ messages
     * are not silently lost.
     * </p>
     *
     * <p>
     * <b>Failure handling:</b><br>
     * If persistence to S3 fails (for example due to transient infrastructure
     * issues), the exception is caught and logged. In this case, the offset is
     * <b>not acknowledged</b>, allowing Kafka to re-deliver the DLQ message based
     * on the consumer configuration.
     * </p>
     *
     * <p>
     * This design provides:
     * </p>
     * <ul>
     *     <li>At-least-once delivery semantics for DLQ records</li>
     *     <li>Protection against silent data loss</li>
     *     <li>Stable DLQ processing without crashing the listener container</li>
     * </ul>
     *
     * @param record         the Kafka {@link ConsumerRecord} containing the failed
     *                       {@link OrderConfirmation} event and its associated metadata
     * @param acknowledgment manual acknowledgment handle used to commit the offset
     *                       after successful persistence
     */
    void consumeOrderDlqMessages(
            final ConsumerRecord<String, OrderConfirmation> record,
            final Acknowledgment acknowledgment
    );
}
