package com.forsaken.ecommerce.order.order.service;

import com.forsaken.ecommerce.avro.CustomerResponse;
import com.forsaken.ecommerce.avro.OrderConfirmation;
import com.forsaken.ecommerce.avro.PaymentMethod;
import com.forsaken.ecommerce.avro.PurchaseResponse;
import com.forsaken.ecommerce.order.configs.kafka.KafkaProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.Message;

import java.nio.ByteBuffer;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.kafka.support.KafkaHeaders.TOPIC;

/**
 * Unit test for {@link OrderProducerImpl}.
 *
 * <p>
 * This test class verifies the behavior of the Kafka producer responsible
 * for publishing {@link OrderConfirmation} events.
 * </p>
 *
 * <p>
 * <b>Test Scope:</b>
 * </p>
 * <ul>
 *   <li>Ensures that an {@link OrderConfirmation} message is sent to Kafka.</li>
 *   <li>Verifies that the Kafka topic is resolved from {@link KafkaProperties}.</li>
 *   <li>Asserts that the message payload and headers are constructed correctly.</li>
 * </ul>
 *
 * <p>
 * <b>Out of Scope:</b>
 * </p>
 * <ul>
 *   <li>Kafka broker connectivity</li>
 *   <li>Serialization / deserialization</li>
 *   <li>Schema compatibility</li>
 * </ul>
 *
 * <p>
 * This is a <b>pure unit test</b> using Mockito. No Spring context or Kafka
 * infrastructure is started.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class OrderProducerImplTest {

    /**
     * Mocked KafkaTemplate used to verify message publishing behavior
     * without interacting with a real Kafka broker.
     */
    @Mock
    private KafkaTemplate<String, OrderConfirmation> kafkaTemplate;

    /**
     * Mocked Kafka configuration properties supplying the topic name.
     */
    @Mock
    private KafkaProperties kafkaProperties;

    /**
     * System under test.
     * Injects mocked dependencies into {@link OrderProducerImpl}.
     */
    @InjectMocks
    private OrderProducerImpl orderProducer;

    /**
     * Sample {@link OrderConfirmation} used as test payload.
     */
    private OrderConfirmation orderConfirmation;

    /**
     * Initializes a fully populated {@link OrderConfirmation} instance
     * used as test input.
     *
     * <p>
     * The object includes:
     * </p>
     * <ul>
     *   <li>Customer information</li>
     *   <li>Payment method</li>
     *   <li>Total amount and product price represented as {@link ByteBuffer}</li>
     *   <li>A list of purchased products</li>
     * </ul>
     *
     * <p>
     * Binary fields are intentionally populated with minimal {@link ByteBuffer}
     * values to keep the test focused on message dispatch behavior rather than
     * numeric precision.
     * </p>
     */
    @BeforeEach
    void setUp() {
        orderConfirmation = OrderConfirmation.newBuilder()
                .setOrderReference("ORD-123")
                .setCustomer(
                        CustomerResponse.newBuilder()
                                .setId("CUST-123")
                                .setFirstname("Test FirstName")
                                .setLastname("Test LastName")
                                .setEmail("noreply@noreply.com")
                                .build()
                )
                .setTotalAmount(ByteBuffer.wrap(new byte[]{0x01}))
                .setPaymentMethod(PaymentMethod.CREDIT_CARD)
                .setProducts(
                        List.of(
                                PurchaseResponse.newBuilder()
                                        .setProductId(1)
                                        .setName("PRODUCT NAME")
                                        .setQuantity(1)
                                        .setPrice(ByteBuffer.wrap(new byte[]{0x01}))
                                        .setDescription("TEST DESCRIPTION")
                                        .build()
                        )
                )
                .build();
    }

    /**
     * Verifies that {@link OrderProducerImpl#sendOrderConfirmation(OrderConfirmation)}
     * publishes a Kafka message with:
     *
     * <ul>
     *   <li>The correct {@link OrderConfirmation} payload</li>
     *   <li>The correct Kafka topic header resolved from {@link KafkaProperties}</li>
     * </ul>
     *
     * <p>
     * This test asserts:
     * </p>
     * <ol>
     *   <li>{@link KafkaTemplate#send(org.springframework.messaging.Message)} is
     *       invoked exactly once</li>
     *   <li>The message payload matches the provided {@link OrderConfirmation}</li>
     *   <li>The {@code KafkaHeaders.TOPIC} header contains the expected topic name</li>
     * </ol>
     *
     * <p>
     * Any deviation (wrong topic, missing header, duplicate sends) will cause
     * the test to fail.
     * </p>
     */
    @Test
    void shouldSendOrderConfirmationToKafkaWithCorrectTopicAndPayload() {
        // given
        final String topicName = "order-confirmation-topic";
        when(kafkaProperties.topicName()).thenReturn(topicName);
        final ArgumentCaptor<Message<OrderConfirmation>> messageCaptor =
                ArgumentCaptor.forClass(Message.class);

        // when
        orderProducer.sendOrderConfirmation(orderConfirmation);

        // then
        verify(kafkaTemplate, times(1)).send(messageCaptor.capture());
        final Message<OrderConfirmation> sentMessage = messageCaptor.getValue();

        assertThat(sentMessage).isNotNull();
        assertThat(sentMessage.getPayload()).isEqualTo(orderConfirmation);
        assertThat(sentMessage.getHeaders().get(TOPIC)).isEqualTo(topicName);
        verifyNoMoreInteractions(kafkaTemplate);
    }
}
