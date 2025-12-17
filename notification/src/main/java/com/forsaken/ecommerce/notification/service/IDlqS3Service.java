package com.forsaken.ecommerce.notification.service;

import com.forsaken.ecommerce.notification.models.EventType;
import org.apache.kafka.clients.consumer.ConsumerRecord;

import java.util.List;

/**
 * Contract for persisting and managing Kafka Dead Letter Queue (DLQ) events
 * in Amazon S3.
 *
 * <p>
 * Implementations of this interface are responsible for reliably storing
 * failed Kafka consumer records into S3 so that they can be:
 * </p>
 * <ul>
 *     <li>Inspected for debugging and root-cause analysis</li>
 *     <li>Replayed or reprocessed at a later time</li>
 *     <li>Cleaned up after successful recovery</li>
 * </ul>
 *
 * <p>
 * The service is typically used in conjunction with Kafka retry and DLQ
 * mechanisms, where records that exhaust retry attempts are offloaded
 * to durable object storage instead of being lost.
 * </p>
 *
 * <p>
 * <b>Storage format:</b>
 * </p>
 * <ul>
 *     <li>Each DLQ record is stored as a JSON document in S3</li>
 *     <li>The JSON payload includes Kafka metadata (topic, partition, offset, key, timestamp)</li>
 *     <li>The original message value is serialized as JSON (Avro-safe conversion)</li>
 * </ul>
 *
 * <p>
 * <b>Thread safety:</b><br>
 * Implementations must be stateless and safe for concurrent use, as they are
 * typically registered as singleton Spring beans and invoked by Kafka
 * listener threads.
 * </p>
 *
 * <p>
 * <b>Error handling:</b><br>
 * Implementations are expected to throw runtime exceptions when S3 operations
 * fail, allowing callers to decide whether to retry, escalate, or log the
 * failure.
 * </p>
 */
public interface IDlqS3Service {

    /**
     * Stores a Kafka consumer record into S3 as a DLQ entry.
     *
     * <p>
     * The stored object contains:
     * </p>
     * <ul>
     *     <li>Kafka metadata (topic, partition, offset, timestamp, key)</li>
     *     <li>The original message value serialized to JSON</li>
     * </ul>
     *
     * <p>
     * The target S3 key is derived from the supplied {@code eventType}
     * (for example, {@code payment} or {@code order}) and a generated
     * unique identifier to avoid collisions.
     * </p>
     *
     * <p>
     * This method is typically invoked when message processing fails
     * permanently after all retry attempts have been exhausted.
     * </p>
     *
     * @param record    the Kafka {@link ConsumerRecord} that failed processing
     * @param eventType a logical event type used to determine the S3 prefix
     *                  (e.g. {@code payment}, {@code order})
     * @throws RuntimeException if the record cannot be serialized or
     *                          the S3 upload fails
     */
    void storeToS3(
            final ConsumerRecord<String, ?> record, final EventType eventType
    );

    /**
     * Lists all S3 object keys stored under the given prefix.
     *
     * <p>
     * This method is commonly used to:
     * </p>
     * <ul>
     *     <li>Discover DLQ records awaiting replay</li>
     *     <li>Build administrative or operational tooling</li>
     * </ul>
     *
     * @param prefix the S3 prefix (folder-like path) under which DLQ
     *               records are stored
     * @return a list of S3 object keys; an empty list if none are found
     */
    List<String> listKeys(final String prefix);

    /**
     * Loads a DLQ record from S3 and returns its JSON content.
     *
     * <p>
     * The returned string represents the full JSON document originally
     * stored by {@link #storeToS3(ConsumerRecord, EventType)}, including
     * Kafka metadata and the serialized message payload.
     * </p>
     *
     * <p>
     * This method is typically used during DLQ replay or manual inspection.
     * </p>
     *
     * @param key the exact S3 object key of the DLQ record
     * @return the JSON content of the stored DLQ record
     * @throws RuntimeException if the object cannot be retrieved from S3
     */
    String load(final String key);

    /**
     * Deletes a DLQ record from S3.
     *
     * <p>
     * This method is usually invoked after a DLQ record has been
     * successfully replayed and processed, ensuring that it is not
     * reprocessed again.
     * </p>
     *
     * <p>
     * Implementations should be idempotent — deleting a non-existent
     * object should not cause application failure.
     * </p>
     *
     * @param key the S3 object key to delete
     */
    void delete(final String key);
}
