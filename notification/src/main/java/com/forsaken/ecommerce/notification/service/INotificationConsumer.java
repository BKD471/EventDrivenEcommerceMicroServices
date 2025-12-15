package com.forsaken.ecommerce.notification.service;

import com.forsaken.ecommerce.avro.OrderConfirmation;
import com.forsaken.ecommerce.avro.PaymentConfirmation;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.messaging.MessagingException;

/**
 * Contract for consuming notification-related Kafka messages.
 * <p>
 * Implementations of this interface are responsible for handling
 * domain-specific notification events published to Kafka topics.
 * </p>
 *
 * <p>
 * <b>Responsibilities:</b>
 * <ul>
 *     <li>Consume and process payment success notification events.</li>
 *     <li>Consume and process order confirmation notification events.</li>
 *     <li>Perform validation, transformation, and downstream notification logic.</li>
 * </ul>
 * </p>
 *
 * <p>
 * <b>Kafka Integration:</b>
 * <ul>
 *     <li>Kafka-specific annotations such as {@code @KafkaListener}
 *     should be applied on the concrete implementation class, not on
 *     this interface.</li>
 *     <li>This interface acts purely as a business contract.</li>
 * </ul>
 * </p>
 *
 * <p>
 * <b>Error Handling:</b>
 * <ul>
 *     <li>Implementations may throw {@link MessagingException} to indicate
 *     failures during message processing.</li>
 *     <li>Such exceptions can be handled via Kafka error handlers,
 *     retries, or routed to a Dead Letter Topic (DLT).</li>
 * </ul>
 * </p>
 */
public interface INotificationConsumer {

    /**
     * Consume and process payment success notification events.
     * <p>
     * This method handles messages produced after a successful payment
     * transaction. Typical responsibilities include:
     * </p>
     * <ul>
     *     <li>Validating the received {@link com.forsaken.ecommerce.avro.PaymentConfirmation} payload.</li>
     *     <li>Triggering downstream notifications (email, SMS, push, etc.).</li>
     *     <li>Updating notification or audit state if required.</li>
     * </ul>
     *
     * @param record the Kafka {@link ConsumerRecord} containing the payment
     *               confirmation event as its value
     */
    void consumePaymentSuccessNotifications(
            final ConsumerRecord<String, PaymentConfirmation> record
    );


    /**
     * Consume and process order confirmation notification events.
     * <p>
     * This method handles messages emitted after an order is successfully
     * created or confirmed. Implementations typically:
     * </p>
     * <ul>
     *     <li>Extract order and customer details from the {@link OrderConfirmation} payload.</li>
     *     <li>Send order confirmation notifications to the customer.</li>
     *     <li>Log or audit the notification delivery status.</li>
     * </ul>
     *
     * @param record the Kafka {@link ConsumerRecord} containing the order
     *               confirmation event as its value
     */
    void consumeOrderConfirmationNotifications(
            final ConsumerRecord<String, OrderConfirmation> record
    );
}
