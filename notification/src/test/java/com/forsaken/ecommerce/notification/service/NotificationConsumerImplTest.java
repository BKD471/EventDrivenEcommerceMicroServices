package com.forsaken.ecommerce.notification.service;

import com.forsaken.ecommerce.avro.CustomerResponse;
import com.forsaken.ecommerce.avro.OrderConfirmation;
import com.forsaken.ecommerce.avro.PaymentConfirmation;
import com.forsaken.ecommerce.notification.configs.kafka.KafkaProperties;
import com.forsaken.ecommerce.notification.models.PaymentMethod;
import com.forsaken.ecommerce.notification.repository.INotificationRepository;

import org.apache.kafka.clients.consumer.ConsumerRecord;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static com.forsaken.ecommerce.notification.mapper.AvroMapper.fromBytes;
import static com.forsaken.ecommerce.notification.models.NotificationType.ORDER_CONFIRMATION;
import static com.forsaken.ecommerce.notification.models.NotificationType.PAYMENT_CONFIRMATION;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link NotificationConsumerImpl}.
 *
 * <p>
 * This test suite verifies the behavior of the Kafka notification consumer
 * responsible for handling payment and order confirmation events.
 * </p>
 *
 * <p>
 * <b>Scope of testing:</b>
 * </p>
 * <ul>
 *     <li>Kafka message consumption logic (without starting Kafka)</li>
 *     <li>Persistence of notification entities</li>
 *     <li>Invocation of downstream email notifications</li>
 *     <li>Correct extraction and transformation of Avro payload data</li>
 * </ul>
 *
 * <p>
 * <b>What is intentionally NOT tested:</b>
 * </p>
 * <ul>
 *     <li>Kafka infrastructure, partitions, or offsets</li>
 *     <li>Spring container lifecycle</li>
 *     <li>Email delivery implementation details</li>
 *     <li>Repository persistence mechanics</li>
 * </ul>
 *
 * <p>
 * <b>Testing strategy:</b>
 * </p>
 * <ul>
 *     <li>Uses {@link org.mockito.junit.jupiter.MockitoExtension}</li>
 *     <li>Mocks all external dependencies</li>
 *     <li>Uses real Avro objects built via Avro builders</li>
 *     <li>Avoids {@code any()} in positive test cases to ensure strict assertions</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class NotificationConsumerImplTest {

    @Mock
    private INotificationRepository notificationRepository;

    @Mock
    private IEmailService emailService;

    @Mock
    private KafkaProperties kafkaProperties;

    @InjectMocks
    private NotificationConsumerImpl consumer;

    /**
     * Verifies that a payment confirmation Kafka message is:
     * <ul>
     *     <li>Consumed successfully</li>
     *     <li>Persisted as a notification entity</li>
     *     <li>Triggers a payment success email with correct parameters</li>
     * </ul>
     *
     * <p>
     * This test ensures:
     * </p>
     * <ul>
     *     <li>Avro {@link PaymentConfirmation} is correctly interpreted</li>
     *     <li>Decimal amount conversion is correct</li>
     *     <li>Payment timestamp is converted using the configured time zone</li>
     *     <li>Email service is invoked exactly once with expected arguments</li>
     * </ul>
     */
    @Test
    void shouldConsumePaymentConfirmationAndSendEmail() {
        // given
        when(kafkaProperties.timeZone()).thenReturn("UTC");
        PaymentConfirmation paymentAvro = constructPaymentConfirmation();
        final ConsumerRecord<String, PaymentConfirmation> record =
                new ConsumerRecord<>("payment-topic", 0, 0L, "key", paymentAvro);
        doNothing().when(notificationRepository)
                .save(argThat(n ->
                        n.getType() == PAYMENT_CONFIRMATION &&
                                n.getPaymentConfirmation() != null
                ));

        // when
        consumer.consumePaymentSuccessNotifications(record);

        // then
        verify(notificationRepository).save(argThat(n ->
                n.getType() == PAYMENT_CONFIRMATION
        ));
        final BigDecimal expectedAmount = fromBytes(paymentAvro.getAmount());
        final LocalDateTime expectedDate = LocalDateTime.ofInstant(
                Instant.parse("2024-01-01T10:00:00Z"),
                ZoneId.of("UTC")
        );
        verify(emailService).sendPaymentSuccessEmail(
                "john@doe.com",
                "John Doe",
                expectedAmount,
                "ORD-100",
                PaymentMethod.PAYPAL,
                expectedDate
        );
    }

    /**
     * Verifies that an order confirmation Kafka message is:
     * <ul>
     *     <li>Consumed successfully</li>
     *     <li>Persisted as an order notification</li>
     *     <li>Triggers an order confirmation email</li>
     * </ul>
     *
     * <p>
     * This test validates:
     * </p>
     * <ul>
     *     <li>Extraction of customer details from nested Avro records</li>
     *     <li>Correct conversion of total amount</li>
     *     <li>Correct propagation of order reference and product list</li>
     * </ul>
     */
    @Test
    void shouldConsumeOrderConfirmationAndSendEmail() {
        // given
        final CustomerResponse customer = constructCustomer();
        final OrderConfirmation orderAvro = constructOrderConfirmation(customer);
        final ConsumerRecord<String, OrderConfirmation> record =
                new ConsumerRecord<>("order-topic", 0, 0L, "key", orderAvro);
        doNothing().when(notificationRepository)
                .save(argThat(n ->
                        n.getType() == ORDER_CONFIRMATION &&
                                n.getOrderConfirmation() != null
                ));

        // when
        consumer.consumeOrderConfirmationNotifications(record);

        // then
        verify(notificationRepository).save(argThat(n ->
                n.getType() == ORDER_CONFIRMATION
        ));
        final BigDecimal expectedAmount = fromBytes(orderAvro.getTotalAmount());
        verify(emailService).sendOrderConfirmationEmail(
                "alice@smith.com",
                "Alice Smith",
                expectedAmount,
                "ORD-200",
                List.of()
        );
    }

    /**
     * Constructs a valid Avro {@link PaymentConfirmation} event
     * for use in payment consumer tests.
     *
     * <p>
     * The returned object satisfies all Avro schema constraints.
     * </p>
     */
    private PaymentConfirmation constructPaymentConfirmation() {
        return PaymentConfirmation.newBuilder()
                .setOrderReference("ORD-100")
                .setAmount(ByteBuffer.wrap(new byte[]{0x01}))
                .setPaymentMethod(com.forsaken.ecommerce.avro.PaymentMethod.PAYPAL)
                .setCustomerFirstname("John")
                .setCustomerLastname("Doe")
                .setCustomerEmail("john@doe.com")
                .setPaymentDate(Instant.parse("2024-01-01T10:00:00Z"))
                .setTraceId("trace-1")
                .build();
    }

    /**
     * Constructs a valid Avro {@link CustomerResponse} record.
     *
     * <p>
     * Used as a nested dependency for order confirmation events.
     * </p>
     */
    private CustomerResponse constructCustomer() {
        return CustomerResponse.newBuilder()
                .setId("cust-1")
                .setFirstname("Alice")
                .setLastname("Smith")
                .setEmail("alice@smith.com")
                .build();
    }

    /**
     * Constructs a valid Avro {@link OrderConfirmation} event
     * using the provided {@link CustomerResponse}.
     *
     * @param customer non-null customer record required by Avro schema
     */
    private OrderConfirmation constructOrderConfirmation(final CustomerResponse customer) {
        return OrderConfirmation.newBuilder()
                .setOrderReference("ORD-200")
                .setCustomer(customer)
                .setTotalAmount(ByteBuffer.wrap(new byte[]{0x02}))
                .setProducts(List.of())
                .setTraceId("trace-2")
                .setPaymentMethod(com.forsaken.ecommerce.avro.PaymentMethod.CREDIT_CARD)
                .build();
    }
}
