package com.forsaken.ecommerce.notification.service;

import com.forsaken.ecommerce.avro.CustomerResponse;
import com.forsaken.ecommerce.avro.OrderConfirmation;
import com.forsaken.ecommerce.avro.PaymentConfirmation;
import com.forsaken.ecommerce.notification.configs.kafka.KafkaDlqProperties;
import com.forsaken.ecommerce.notification.configs.kafka.KafkaProperties;
import com.forsaken.ecommerce.notification.models.PaymentMethod;
import com.forsaken.ecommerce.notification.repository.INotificationRepository;

import org.apache.kafka.clients.consumer.ConsumerRecord;

import org.junit.jupiter.api.BeforeEach;
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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit test configuration for {@link NotificationConsumerImpl}.
 *
 * <p>
 * This test class uses {@link org.mockito.junit.jupiter.MockitoExtension}
 * to create an isolated test environment where all external dependencies
 * of the notification consumer are mocked.
 * </p>
 *
 * <p>
 * <b>Mocked dependencies:</b>
 * </p>
 * <ul>
 *     <li>{@link INotificationRepository} – prevents real persistence</li>
 *     <li>{@link IEmailService} – prevents real email delivery</li>
 *     <li>{@link KafkaProperties} – provides required configuration values</li>
 * </ul>
 *
 * <p>
 * The {@link NotificationConsumerImpl} under test is instantiated using
 * {@link InjectMocks}, allowing Mockito to inject all mocked dependencies
 * automatically.
 * </p>
 *
 * <p>
 * This setup ensures that tests focus exclusively on the consumer’s
 * business behavior without relying on infrastructure components.
 * </p>
 * <p>
 * <b>Dead Letter Queue (DLQ) handling:</b>
 * </p>
 *
 * <p>
 * This test suite explicitly covers DLQ consumers for payment and order events.
 * DLQ listeners are intentionally designed to be minimal and deterministic:
 * </p>
 *
 * <ul>
 *     <li>No validation or business processing is performed</li>
 *     <li>No database writes or email notifications are triggered</li>
 *     <li>The original {@link ConsumerRecord} is persisted verbatim to S3</li>
 * </ul>
 *
 * <p>
 * This guarantees that messages exceeding retry limits are preserved
 * for audit, debugging, and manual replay without risk of data loss.
 * </p>
 * <p>
 * <b>Kafka acknowledgment and retry scope:</b>
 * </p>
 *
 * <p>
 * These tests invoke consumer methods directly and do not validate
 * Kafka acknowledgment, retry, or backoff behavior.
 * </p>
 *
 * <p>
 * Such concerns are handled by Spring Kafka configuration
 * (error handlers, retry policies, and DLQ routing) and are
 * intentionally tested at the integration level rather than
 * in unit tests.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class NotificationConsumerImplTest {

    @Mock
    private INotificationRepository notificationRepository;

    @Mock
    private IEmailService emailService;

    @Mock
    private IDlqS3Service dlqS3Service;

    @Mock
    private KafkaProperties kafkaProperties;

    @Mock
    private KafkaDlqProperties dlqProperties;

    @InjectMocks
    private NotificationConsumerImpl consumer;

    /**
     * Initializes common test configuration before each test execution.
     *
     * <p>
     * The notification consumer relies on a configured time zone when
     * converting event timestamps. Since {@link KafkaProperties} is mocked,
     * this value must be explicitly provided to avoid {@link NullPointerException}s.
     * </p>
     *
     * <p>
     * Using {@code @BeforeEach} ensures:
     * </p>
     * <ul>
     *     <li>The configuration is applied consistently across all tests</li>
     *     <li>Test methods remain clean and free of duplicated setup code</li>
     *     <li>Future tests do not accidentally fail due to missing configuration</li>
     * </ul>
     */
    @BeforeEach
    void setup() {
        when(kafkaProperties.timeZone()).thenReturn("UTC");
    }

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
        final PaymentConfirmation paymentAvro = constructPaymentConfirmation();
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
     * Verifies that the payment notification consumer remains stable when
     * email delivery fails unexpectedly.
     *
     * <p>
     * This test simulates a runtime failure (for example, SMTP outage or
     * downstream email service error) during invocation of
     * {@link IEmailService#sendPaymentSuccessEmail}.
     * </p>
     *
     * <p>
     * <b>Expected behavior:</b>
     * </p>
     * <ul>
     *     <li>The exception is caught internally by the consumer</li>
     *     <li>The Kafka listener method does NOT propagate the exception</li>
     *     <li>The notification record is still persisted</li>
     *     <li>The email sending attempt is made exactly once</li>
     * </ul>
     *
     * <p>
     * <b>Why this matters:</b>
     * </p>
     * <ul>
     *     <li>Kafka listeners must never throw unchecked exceptions</li>
     *     <li>Throwing would cause partition revocation and repeated reprocessing</li>
     *     <li>Failures must be handled gracefully and logged for observability</li>
     * </ul>
     *
     * <p>
     * The test uses {@code assertDoesNotThrow} to explicitly guarantee
     * consumer stability under failure conditions.
     * </p>
     */
    @Test
    void shouldNotFailConsumerWhenPaymentEmailSendingThrowsException() {
        // given
        final PaymentConfirmation paymentAvro = constructPaymentConfirmation();
        final ConsumerRecord<String, PaymentConfirmation> record =
                new ConsumerRecord<>("payment-topic", 0, 0L, "key", paymentAvro);
        doNothing().when(notificationRepository)
                .save(argThat(n ->
                        n.getType() == PAYMENT_CONFIRMATION &&
                                n.getPaymentConfirmation() != null
                ));
        doThrow(new RuntimeException("SMTP server down"))
                .when(emailService)
                .sendPaymentSuccessEmail(
                        eq("john@doe.com"),
                        eq("John Doe"),
                        eq(fromBytes(paymentAvro.getAmount())),
                        eq("ORD-100"),
                        eq(PaymentMethod.PAYPAL),
                        any(LocalDateTime.class) // date conversion is not the focus here
                );

        // when + then
        assertDoesNotThrow(() ->
                consumer.consumePaymentSuccessNotifications(record)
        );
        // then
        verify(notificationRepository).save(argThat(n ->
                n.getType() == PAYMENT_CONFIRMATION
        ));
        verify(emailService).sendPaymentSuccessEmail(
                eq("john@doe.com"),
                eq("John Doe"),
                eq(fromBytes(paymentAvro.getAmount())),
                eq("ORD-100"),
                eq(PaymentMethod.PAYPAL),
                any(LocalDateTime.class)
        );
    }

    /**
     * Verifies that the order confirmation consumer does not fail when
     * sending order confirmation emails throws an exception.
     *
     * <p>
     * This test forces a runtime exception from
     * {@link IEmailService#sendOrderConfirmationEmail} to ensure:
     * </p>
     *
     * <ul>
     *     <li>The exception is handled inside the Kafka listener</li>
     *     <li>The consumer continues processing without crashing</li>
     *     <li>The notification entity is still persisted</li>
     *     <li>Customer email delivery is attempted exactly once</li>
     * </ul>
     *
     * <p>
     * <b>Kafka safety guarantee:</b>
     * </p>
     * <ul>
     *     <li>Unchecked exceptions must not escape Kafka listener methods</li>
     *     <li>Escaping exceptions would cause consumer restart loops</li>
     *     <li>This test ensures resilience against transient downstream failures</li>
     * </ul>
     *
     * <p>
     * This test intentionally validates behavior under failure rather than
     * successful email delivery.
     * </p>
     */
    @Test
    void shouldNotFailConsumerWhenOrderEmailSendingThrowsException() {
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
        doThrow(new RuntimeException("SMTP server down"))
                .when(emailService)
                .sendOrderConfirmationEmail(
                        eq("alice@smith.com"),
                        eq("Alice Smith"),
                        eq(fromBytes(orderAvro.getTotalAmount())),
                        eq("ORD-200"),
                        eq(List.of())
                );

        // when + then
        assertDoesNotThrow(() ->
                consumer.consumeOrderConfirmationNotifications(record)
        );
        // then
        verify(notificationRepository).save(argThat(n ->
                n.getType() == ORDER_CONFIRMATION
        ));
        verify(emailService).sendOrderConfirmationEmail(
                eq("alice@smith.com"),
                eq("Alice Smith"),
                eq(fromBytes(orderAvro.getTotalAmount())),
                eq("ORD-200"),
                eq(List.of())
        );
    }

    /**
     * Verifies that the payment notification consumer safely ignores
     * Kafka records with a null {@link PaymentConfirmation} payload.
     *
     * <p>
     * Expected behavior:
     * <ul>
     *     <li>The consumer does not throw any exception</li>
     *     <li>No notification is persisted</li>
     *     <li>No email is sent</li>
     * </ul>
     * </p>
     *
     * <p>
     * This scenario can occur due to:
     * <ul>
     *     <li>Deserialization failures</li>
     *     <li>Poison messages</li>
     *     <li>Upstream producer bugs</li>
     * </ul>
     * </p>
     */
    @Test
    void shouldSkipProcessingWhenPaymentConfirmationIsNull() {
        // given
        final ConsumerRecord<String, PaymentConfirmation> record =
                new ConsumerRecord<>("payment-topic", 0, 0L, "key", null);

        // when -> then
        assertDoesNotThrow(() ->
                consumer.consumePaymentSuccessNotifications(record)
        );
        verify(notificationRepository, never()).save(any());
        verify(emailService, never())
                .sendPaymentSuccessEmail(
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any()
                );
    }

    /**
     * Verifies that the order notification consumer safely ignores
     * Kafka records with a null {@link OrderConfirmation} payload.
     *
     * <p>
     * Expected behavior:
     * <ul>
     *     <li>The consumer does not throw any exception</li>
     *     <li>No notification is persisted</li>
     *     <li>No order confirmation email is sent</li>
     * </ul>
     * </p>
     *
     * <p>
     * This scenario can occur due to deserialization issues,
     * malformed Kafka messages, or upstream producer bugs.
     * </p>
     */
    @Test
    void shouldSkipProcessingWhenOrderConfirmationIsNull() {
        // given
        final ConsumerRecord<String, OrderConfirmation> record =
                new ConsumerRecord<>("order-topic", 0, 0L, "key", null);

        // when -> then
        assertDoesNotThrow(() ->
                consumer.consumeOrderConfirmationNotifications(record)
        );
        verify(notificationRepository, never()).save(any());
        verify(emailService, never())
                .sendOrderConfirmationEmail(
                        any(),
                        any(),
                        any(),
                        any(),
                        any()
                );
    }

    /**
     * Verifies that a payment-related Dead Letter Queue (DLQ) message
     * is persisted to S3 for later inspection or replay.
     *
     * <p>
     * This test ensures that when a {@link PaymentConfirmation} record
     * is consumed from the payment DLQ topic:
     * </p>
     *
     * <ul>
     *     <li>The consumer does not attempt normal processing (email, DB, etc.).</li>
     *     <li>The original Kafka {@link ConsumerRecord} is forwarded as-is
     *         to {@link IDlqS3Service}.</li>
     *     <li>The event is categorized correctly using the {@code "payment"}
     *         DLQ prefix.</li>
     * </ul>
     *
     * <p>
     * <b>Why this matters:</b>
     * </p>
     * <ul>
     *     <li>Preserves failed payment events for audit and replay.</li>
     *     <li>Ensures no data loss for messages that exceeded retry limits.</li>
     *     <li>Guarantees consistent DLQ handling behavior.</li>
     * </ul>
     * <p>
     * DLQ consumers are intentionally allowed to fail fast if S3 persistence
     * fails. Swallowing such failures would result in silent data loss and
     * is therefore avoided by design.
     * </p>
     */
    @Test
    void paymentConsumeDlq_shouldStoreRecordInS3() {
        // given
        final PaymentConfirmation paymentConfirmation = constructPaymentConfirmation();
        final ConsumerRecord<String, PaymentConfirmation> record =
                new ConsumerRecord<>(
                        "payment-dlq-topic",
                        0,
                        10L,
                        "key-1",
                        paymentConfirmation
                );

        // when
        consumer.paymentConsumeDlq(record);

        // then
        verify(dlqS3Service).storeToS3(record, "payment");
    }

    /**
     * Verifies that an order-related Dead Letter Queue (DLQ) message
     * is persisted to S3 for recovery or manual replay.
     *
     * <p>
     * This test validates that when an {@link OrderConfirmation} record
     * is received from the order DLQ topic:
     * </p>
     *
     * <ul>
     *     <li>The consumer skips normal order notification processing.</li>
     *     <li>The full Kafka {@link ConsumerRecord} is stored without modification.</li>
     *     <li>The event is stored under the {@code "order"} DLQ prefix
     *         to allow logical separation from payment DLQ events.</li>
     * </ul>
     *
     * <p>
     * <b>Operational importance:</b>
     * </p>
     * <ul>
     *     <li>Allows safe replay of failed order notifications.</li>
     *     <li>Prevents repeated Kafka retries for permanently failing records.</li>
     *     <li>Improves observability and operational debugging.</li>
     * </ul>
     */
    @Test
    void orderConsumeDlq_shouldStoreRecordInS3() {
        // given
        final CustomerResponse customer = constructCustomer();
        final OrderConfirmation orderConfirmation = constructOrderConfirmation(customer);

        final ConsumerRecord<String, OrderConfirmation> record =
                new ConsumerRecord<>(
                        "order-dlq-topic",
                        1,
                        22L,
                        "key-2",
                        orderConfirmation
                );

        // when
        consumer.orderConsumeDlq(record);

        // then
        verify(dlqS3Service).storeToS3(record, "order");
    }

    /**
     * Verifies that the payment DLQ consumer fails fast when persisting
     * a DLQ record to S3 fails.
     *
     * <p>
     * This test validates the following behavior:
     * </p>
     * <ul>
     *     <li>The DLQ consumer attempts to persist the failed
     *         {@link PaymentConfirmation} record to S3.</li>
     *     <li>If {@link IDlqS3Service#storeToS3} throws an exception,
     *         the exception is re-thrown by the consumer.</li>
     *     <li>The exception is not swallowed, ensuring Kafka retry or
     *         error-handling mechanisms are triggered.</li>
     *     <li>No normal processing logic (database persistence or
     *         email sending) is executed for DLQ records.</li>
     * </ul>
     *
     * <p>
     * <b>Why this matters:</b>
     * </p>
     * <ul>
     *     <li>DLQ events represent already-failed messages and must
     *         never be silently dropped.</li>
     *     <li>Fail-fast behavior guarantees visibility of DLQ persistence
     *         failures.</li>
     *     <li>Ensures operational safety by preventing silent data loss.</li>
     * </ul>
     */
    @Test
    void paymentConsumeDlq_shouldRethrowExceptionWhenS3Fails() {
        // given
        final PaymentConfirmation paymentConfirmation = constructPaymentConfirmation();
        final ConsumerRecord<String, PaymentConfirmation> record =
                new ConsumerRecord<>(
                        "payment-dlq-topic",
                        0,
                        10L,
                        "key-1",
                        paymentConfirmation
                );
        doThrow(new RuntimeException("S3 unavailable"))
                .when(dlqS3Service)
                .storeToS3(record, "payment");

        // when -> then
        assertThrows(RuntimeException.class, () ->
                consumer.paymentConsumeDlq(record)
        );
        // verify S3 attempt happened
        verify(dlqS3Service).storeToS3(record, "payment");
        // verify no normal processing happened
        verifyNoInteractions(notificationRepository);
        verifyNoInteractions(emailService);
    }

    /**
     * Verifies that the order DLQ consumer re-throws exceptions when
     * persisting DLQ records to S3 fails.
     *
     * <p>
     * This test ensures that:
     * </p>
     * <ul>
     *     <li>The {@link OrderConfirmation} DLQ record is passed
     *         unchanged to {@link IDlqS3Service}.</li>
     *     <li>Any exception thrown while storing the record in S3
     *         is propagated back to the Kafka listener.</li>
     *     <li>The consumer does not attempt any normal order notification
     *         processing (database writes or email delivery).</li>
     * </ul>
     *
     * <p>
     * <b>Operational significance:</b>
     * </p>
     * <ul>
     *     <li>Prevents silent loss of order-related DLQ messages.</li>
     *     <li>Allows Kafka retry policies or error handlers to take action.</li>
     *     <li>Preserves failed events for later investigation or replay.</li>
     * </ul>
     */
    @Test
    void orderConsumeDlq_shouldRethrowExceptionWhenS3Fails() {
        // given
        final CustomerResponse customer = constructCustomer();
        final OrderConfirmation orderConfirmation = constructOrderConfirmation(customer);
        final ConsumerRecord<String, OrderConfirmation> record =
                new ConsumerRecord<>(
                        "order-dlq-topic",
                        1,
                        22L,
                        "key-2",
                        orderConfirmation
                );
        doThrow(new RuntimeException("S3 write failed"))
                .when(dlqS3Service)
                .storeToS3(record, "order");

        // when -> then
        assertThrows(RuntimeException.class, () ->
                consumer.orderConsumeDlq(record)
        );

        // verify S3 attempt happened
        verify(dlqS3Service).storeToS3(record, "order");
        // DLQ path must not trigger normal flows
        verifyNoInteractions(notificationRepository);
        verifyNoInteractions(emailService);
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
