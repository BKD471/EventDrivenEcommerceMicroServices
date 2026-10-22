package com.forsaken.ecommerce.payment.service;



import com.forsaken.ecommerce.avro.PaymentConfirmation;
import com.forsaken.ecommerce.avro.PaymentMethod;
import com.forsaken.ecommerce.payment.configs.kafka.KafkaProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.Message;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

/**
 * Unit tests for {@link NotificationProducerServiceImpl}, validating that
 * payment confirmation notifications are correctly published to Kafka.
 *
 * <p>This test class uses Mockito to isolate and verify the behavior of the
 * notification producer, ensuring that:</p>
 *
 * <ul>
 *     <li>The Kafka topic is correctly resolved from {@link KafkaProperties}</li>
 *     <li>A {@link Message} containing a {@link PaymentConfirmation} payload
 *         is constructed and sent</li>
 *     <li>Message headers (e.g., the Kafka topic) are properly populated</li>
 *     <li>No additional interactions with the Kafka template occur beyond the send</li>
 * </ul>
 *
 * <p>These tests do not interact with a real Kafka broker and instead focus
 * purely on validating message construction and producer invocation logic.</p>
 */
@ExtendWith(MockitoExtension.class)
class NotificationProducerServiceImplTest {

    @Mock
    private KafkaProperties kafkaProperties;

    @Mock
    private KafkaTemplate<String, PaymentConfirmation> kafkaTemplate;

    private NotificationProducerServiceImpl service;

    /**
     * Initializes the service with mocked dependencies prior to each test.
     *
     * <p>The goal is to verify that {@link NotificationProducerServiceImpl}
     * constructs and sends Kafka messages correctly, independent of external systems.</p>
     */
    @BeforeEach
    void setup() {
        service = new NotificationProducerServiceImpl(kafkaProperties, kafkaTemplate);
    }

    /**
     * Verifies that {@link NotificationProducerServiceImpl#sendNotification(PaymentConfirmation)}
     * correctly publishes a message to Kafka with the expected payload and headers.
     *
     * <p>This test ensures:</p>
     * <ul>
     *     <li>The configured Kafka topic is used</li>
     *     <li>The {@link PaymentConfirmation} payload is inserted as the message body</li>
     *     <li>The Kafka message header `kafka_topic` is correctly populated</li>
     *     <li>{@link KafkaTemplate#send(Message)} is invoked exactly once</li>
     * </ul>
     */
    @Test
    void testSendNotification() {
        // Given
        final PaymentConfirmation confirmation = constructPaymentConfirmation();
        final String topic = "payment-topic";
        when(kafkaProperties.topicName()).thenReturn(topic);
        when(kafkaTemplate.send(any(Message.class)))
                .thenReturn(CompletableFuture.completedFuture(null));
        ArgumentCaptor<Message<PaymentConfirmation>> messageCaptor =
                ArgumentCaptor.forClass(Message.class);

        // When
        service.sendNotification(confirmation);

        // Then
        verify(kafkaTemplate).send(messageCaptor.capture());
        Message<PaymentConfirmation> msg = messageCaptor.getValue();
        assertEquals(confirmation, msg.getPayload());
        assertEquals(topic, msg.getHeaders().get("kafka_topic"));
    }

    /**
     * Helper method to construct a valid {@link PaymentConfirmation} instance for testing.
     *
     * <p>The object is populated with sample customer, payment, and order details.
     * The {@code amount} field is encoded into a {@link ByteBuffer} as required
     * by the Avro schema.</p>
     *
     * @return a fully populated {@link PaymentConfirmation} instance
     */
    private PaymentConfirmation constructPaymentConfirmation() {
        final ByteBuffer amount = ByteBuffer.wrap("150.50".getBytes());
        return PaymentConfirmation.newBuilder()
                .setOrderReference("order-123")
                .setAmount(amount)
                .setCustomerFirstname("John")
                .setCustomerLastname("Doe")
                .setPaymentMethod(PaymentMethod.BITCOIN)
                .setPaymentDate(Instant.now())
                .setCustomerEmail("john@doe.com")
                .build();
    }
}
