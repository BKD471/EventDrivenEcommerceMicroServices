package com.forsaken.ecommerce.notification.configs.s3;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for storing failed Kafka messages in
 * Amazon S3 as part of the Dead Letter Queue (DLQ) strategy.
 *
 * <p>
 * These properties define where and how failed events are persisted
 * in S3 after all retry attempts are exhausted.
 * </p>
 *
 * <p>
 * The DLQ S3 bucket and prefixes are used by the application to:
 * </p>
 * <ul>
 *     <li>Persist failed <b>payment</b> events for later inspection</li>
 *     <li>Persist failed <b>order</b> events separately</li>
 *     <li>Enable operational debugging, reprocessing, and auditing</li>
 * </ul>
 *
 * <p>
 * <b>Property mapping:</b>
 * </p>
 * <pre>
 * aws:
 *   s3:
 *     dlq:
 *       bucket: payment-dlq-bucket
 *       paymentPrefix: payments/
 *       orderPrefix: orders/
 * </pre>
 *
 * <p>
 * <b>Validation:</b>
 * </p>
 * <ul>
 *     <li>All fields are mandatory and must be non-empty</li>
 *     <li>Validation is enforced at application startup via
 *         {@link org.springframework.validation.annotation.Validated}</li>
 * </ul>
 *
 * <p>
 * <b>Design considerations:</b>
 * </p>
 * <ul>
 *     <li>Separating payment and order prefixes avoids data mixing</li>
 *     <li>S3-based DLQ provides durability beyond Kafka retention</li>
 *     <li>Allows manual replay or batch reprocessing of failed events</li>
 * </ul>
 */
@Validated
@ConfigurationProperties(prefix = "aws.s3.dlq")
public record DlqS3Properties(

        /**
         * Name of the Amazon S3 bucket used to store DLQ messages.
         *
         * <p>
         * This bucket should be dedicated to DLQ usage and protected
         * with restricted access policies.
         * </p>
         */
        @NotBlank
        String bucket,

        /**
         * S3 key prefix under which failed payment-related events
         * are stored.
         *
         * <p>
         * Example: {@code payments/2024/01/}
         * </p>
         */
        @NotBlank
        String paymentPrefix,

        /**
         * S3 key prefix under which failed order-related events
         * are stored.
         *
         * <p>
         * Example: {@code orders/2024/01/}
         * </p>
         */
        @NotBlank
        String orderPrefix
) {
}
