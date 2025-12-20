package com.forsaken.ecommerce.notification.service;

import com.forsaken.ecommerce.avro.CustomerResponse;
import com.forsaken.ecommerce.avro.OrderConfirmation;
import com.forsaken.ecommerce.avro.PaymentConfirmation;
import com.forsaken.ecommerce.notification.configs.kafka.KafkaProperties;
import com.forsaken.ecommerce.notification.models.EventType;
import com.forsaken.ecommerce.notification.models.PaymentMethod;
import com.forsaken.ecommerce.notification.repository.INotificationRepository;

import org.apache.kafka.clients.consumer.ConsumerRecord;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static com.forsaken.ecommerce.notification.mapper.AvroMapper.fromBytes;
import static com.forsaken.ecommerce.notification.models.EventType.ORDER;
import static com.forsaken.ecommerce.notification.models.EventType.PAYMENT;
import static com.forsaken.ecommerce.notification.models.NotificationType.ORDER_CONFIRMATION;
import static com.forsaken.ecommerce.notification.models.NotificationType.PAYMENT_CONFIRMATION;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link NotificationConsumerImpl}.
 *
 * <p>
 * This test class validates the behavior of Kafka listener methods responsible
 * for consuming payment and order notification events, as well as their
 * corresponding Dead Letter Queue (DLQ) consumers.
 * </p>
 *
 * <h2>Test scope</h2>
 *
 * <p>
 * These are <b>pure unit tests</b>. Kafka infrastructure, retry policies,
 * error handlers, and acknowledgment semantics provided by Spring Kafka
 * are intentionally <b>out of scope</b> and mocked.
 * </p>
 *
 * <p>
 * The tests invoke listener methods directly and verify:
 * </p>
 * <ul>
 *     <li>Business logic execution</li>
 *     <li>Correct interaction with repositories and email services</li>
 *     <li>Explicit acknowledgment behavior</li>
 *     <li>Exception propagation or suppression as designed</li>
 * </ul>
 *
 * <h2>Mocked dependencies</h2>
 *
 * <ul>
 *     <li>{@link INotificationRepository} – prevents real persistence</li>
 *     <li>{@link IEmailService} – prevents external email delivery</li>
 *     <li>{@link IDlqS3Service} – prevents real S3 interactions</li>
 *     <li>{@link KafkaProperties} – supplies required configuration values</li>
 *     <li>{@link Acknowledgment} – verifies manual offset control</li>
 * </ul>
 *
 * <h2>Kafka acknowledgment strategy</h2>
 *
 * <p>
 * The production consumer uses <b>manual acknowledgment</b>. These tests verify:
 * </p>
 *
 * <ul>
 *     <li>Acknowledgment occurs only after successful processing</li>
 *     <li>No acknowledgment occurs when processing fails</li>
 *     <li>Null (tombstone) records are acknowledged immediately</li>
 * </ul>
 *
 * <p>
 * Actual Kafka commit behavior, retries, and DLQ routing are enforced by
 * Spring Kafka configuration and are validated separately via integration tests.
 * </p>
 *
 * <h2>Error handling philosophy</h2>
 *
 * <ul>
 *     <li>Primary consumers (payment/order):
 *         <ul>
 *             <li>Throw exceptions on processing failures</li>
 *             <li>Allow Spring Kafka to trigger retries or DLQ routing</li>
 *         </ul>
 *     </li>
 *     <li>DLQ consumers:
 *         <ul>
 *             <li>Persist failed records to S3</li>
 *             <li>Acknowledge only after successful persistence</li>
 *             <li>Fail fast if DLQ persistence itself fails</li>
 *         </ul>
 *     </li>
 * </ul>
 *
 * <h2>Null (tombstone) record handling</h2>
 *
 * <p>
 * Kafka records with {@code null} payloads are treated as non-recoverable
 * (e.g., compaction tombstones or producer bugs). The consumer:
 * </p>
 *
 * <ul>
 *     <li>Logs the occurrence for observability</li>
 *     <li>Skips all business processing</li>
 *     <li>Acknowledges immediately to avoid infinite retry loops</li>
 * </ul>
 *
 * <h2>Why this matters</h2>
 *
 * <p>
 * This test suite ensures that:
 * </p>
 * <ul>
 *     <li>Consumers are resilient to downstream failures</li>
 *     <li>No message is silently dropped</li>
 *     <li>DLQ events are preserved safely</li>
 *     <li>Kafka consumer stability is maintained</li>
 * </ul>
 *
 * <p>
 * Together, these tests enforce correctness, safety, and operational
 * predictability of the notification service.
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
    private Acknowledgment acknowledgment;

    @InjectMocks
    private NotificationConsumerImpl consumer;

    /**
     * Initializes common test configuration before each test execution.
     *
     * <p>
     * {@link NotificationConsumerImpl} relies on a configured time zone when
     * converting Kafka record timestamps into {@link LocalDateTime}.
     * </p>
     *
     * <p>
     * Since {@link KafkaProperties} is mocked in unit tests, the time zone
     * must be explicitly defined to prevent {@link NullPointerException}s
     * during date conversion.
     * </p>
     *
     * <p>
     * Using {@code @BeforeEach} ensures:
     * </p>
     * <ul>
     *     <li>Consistent configuration across all test cases</li>
     *     <li>No duplication of setup logic inside individual tests</li>
     *     <li>Future tests do not fail unexpectedly due to missing configuration</li>
     * </ul>
     */
    @BeforeEach
    void setup() {
        when(kafkaProperties.timeZone()).thenReturn("UTC");
    }

    /**
     * Verifies successful processing of a payment confirmation Kafka message.
     *
     * <p>
     * Expected behavior:
     * </p>
     * <ul>
     *     <li>The payment confirmation is persisted as a notification</li>
     *     <li>A payment success email is sent with correct parameters</li>
     *     <li>The Kafka offset is acknowledged after successful processing</li>
     * </ul>
     *
     * <p>
     * This test validates:
     * </p>
     * <ul>
     *     <li>Correct interpretation of Avro {@link PaymentConfirmation}</li>
     *     <li>Accurate monetary amount conversion</li>
     *     <li>Timestamp conversion using the configured time zone</li>
     *     <li>Explicit manual acknowledgment on success</li>
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
        consumer.consumePaymentSuccessNotifications(record, acknowledgment);

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
        verify(acknowledgment, atLeastOnce()).acknowledge();
    }

    /**
     * Verifies successful processing of an order confirmation Kafka message.
     *
     * <p>
     * Expected behavior:
     * </p>
     * <ul>
     *     <li>The order confirmation is persisted as a notification</li>
     *     <li>An order confirmation email is sent</li>
     *     <li>The Kafka offset is acknowledged after successful processing</li>
     * </ul>
     *
     * <p>
     * This test ensures correct extraction of nested customer data,
     * correct amount conversion, and correct propagation of order metadata.
     * </p>
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
        consumer.consumeOrderConfirmationNotifications(record, acknowledgment);

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
        verify(acknowledgment, atLeastOnce()).acknowledge();
    }

    /**
     * Verifies behavior when payment email delivery fails during processing.
     *
     * <p>
     * This test simulates a runtime failure (e.g. SMTP outage) occurring
     * after the notification entity has already been persisted.
     * </p>
     *
     * <p>
     * Expected behavior:
     * </p>
     * <ul>
     *     <li>The exception is propagated out of the Kafka listener</li>
     *     <li>The notification record is still persisted</li>
     *     <li>The email send attempt occurs exactly once</li>
     *     <li>The Kafka offset is <b>not acknowledged</b></li>
     * </ul>
     *
     * <p>
     * <b>Why this matters:</b>
     * </p>
     * <ul>
     *     <li>Allows Spring Kafka retry / DLQ mechanisms to activate</li>
     *     <li>Prevents silent loss of failed notifications</li>
     *     <li>Ensures at-least-once delivery semantics</li>
     * </ul>
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
        assertThrows(RuntimeException.class, () ->
                consumer.consumePaymentSuccessNotifications(record, acknowledgment)
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
        verify(acknowledgment, never()).acknowledge();
    }

    /**
     * Verifies behavior when order confirmation email delivery fails.
     *
     * <p>
     * This test forces a runtime exception during email sending to ensure
     * the listener does not acknowledge the Kafka offset on failure.
     * </p>
     *
     * <p>
     * Expected behavior:
     * </p>
     * <ul>
     *     <li>The exception is propagated</li>
     *     <li>The notification entity is persisted</li>
     *     <li>The email send attempt occurs exactly once</li>
     *     <li>No acknowledgment is performed</li>
     * </ul>
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
        assertThrows(RuntimeException.class, () ->
                consumer.consumeOrderConfirmationNotifications(record, acknowledgment)
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
        verify(acknowledgment, never()).acknowledge();
    }

    /**
     * Verifies that Kafka records with a {@code null} {@link PaymentConfirmation}
     * payload are safely ignored.
     *
     * <p>
     * Null payloads may occur due to:
     * </p>
     * <ul>
     *     <li>Kafka compaction tombstone records</li>
     *     <li>Upstream producer bugs</li>
     *     <li>Deserialization failures</li>
     * </ul>
     *
     * <p>
     * Expected behavior:
     * </p>
     * <ul>
     *     <li>No exception is thrown</li>
     *     <li>No persistence or email logic is executed</li>
     *     <li>The offset is acknowledged immediately</li>
     * </ul>
     *
     * <p>
     * These records are considered non-recoverable and must not be retried.
     * </p>
     */
    @Test
    void shouldSkipProcessingWhenPaymentConfirmationIsNull() {
        // given
        final ConsumerRecord<String, PaymentConfirmation> record =
                new ConsumerRecord<>("payment-topic", 0, 0L, "key", null);

        // when -> then
        assertDoesNotThrow(() ->
                consumer.consumePaymentSuccessNotifications(record, acknowledgment)
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
        verify(acknowledgment, atLeastOnce()).acknowledge();
    }

    /**
     * Verifies that Kafka records with a {@code null} {@link OrderConfirmation}
     * payload are safely ignored.
     *
     * <p>
     * Null payloads may occur due to:
     * </p>
     * <ul>
     *     <li>Kafka compaction tombstone records</li>
     *     <li>Upstream producer bugs</li>
     *     <li>Deserialization failures</li>
     * </ul>
     *
     * <p>
     * Expected behavior:
     * </p>
     * <ul>
     *     <li>No exception is thrown</li>
     *     <li>No persistence or email logic is executed</li>
     *     <li>The offset is acknowledged immediately</li>
     * </ul>
     *
     * <p>
     * These records are considered non-recoverable and must not be retried.
     * </p>
     */
    @Test
    void shouldSkipProcessingWhenOrderConfirmationIsNull() {
        // given
        final ConsumerRecord<String, OrderConfirmation> record =
                new ConsumerRecord<>("order-topic", 0, 0L, "key", null);

        // when -> then
        assertDoesNotThrow(() ->
                consumer.consumeOrderConfirmationNotifications(record, acknowledgment)
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
        verify(acknowledgment, atLeastOnce()).acknowledge();
    }

    /**
     * Verifies that a payment-related Dead Letter Queue (DLQ) message
     * is persisted to S3.
     *
     * <p>
     * DLQ consumers are intentionally minimal and deterministic:
     * </p>
     * <ul>
     *     <li>No validation or business logic</li>
     *     <li>No database writes</li>
     *     <li>No email delivery</li>
     * </ul>
     *
     * <p>
     * Expected behavior:
     * </p>
     * <ul>
     *     <li>The original Kafka {@link ConsumerRecord} is persisted verbatim</li>
     *     <li>The record is categorized using {@link EventType#PAYMENT}</li>
     *     <li>The offset is acknowledged only after successful persistence</li>
     * </ul>
     */
    @Test
    void paymentConsumeDlqShouldStoreRecordInS3() throws IOException {
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
        consumer.consumePaymentDlqMessages(record, acknowledgment);

        // then
        verify(dlqS3Service).storeToS3(record, PAYMENT);
        verify(acknowledgment, atLeastOnce()).acknowledge();
    }

    /**
     * Verifies that a order-related Dead Letter Queue (DLQ) message
     * is persisted to S3.
     *
     * <p>
     * DLQ consumers are intentionally minimal and deterministic:
     * </p>
     * <ul>
     *     <li>No validation or business logic</li>
     *     <li>No database writes</li>
     *     <li>No email delivery</li>
     * </ul>
     *
     * <p>
     * Expected behavior:
     * </p>
     * <ul>
     *     <li>The original Kafka {@link ConsumerRecord} is persisted verbatim</li>
     *     <li>The record is categorized using {@link EventType#PAYMENT}</li>
     *     <li>The offset is acknowledged only after successful persistence</li>
     * </ul>
     */
    @Test
    void orderConsumeDlqShouldStoreRecordInS3() throws IOException {
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
        consumer.consumeOrderDlqMessages(record, acknowledgment);

        // then
        verify(dlqS3Service).storeToS3(record, ORDER);
        verify(acknowledgment, atLeastOnce()).acknowledge();
    }

    /**
     * Verifies fail-fast behavior when DLQ persistence to S3 fails.
     *
     * <p>
     * DLQ messages represent already-failed records and must never
     * be silently dropped.
     * </p>
     *
     * <p>
     * Expected behavior:
     * </p>
     * <ul>
     *     <li>The S3 persistence attempt is made</li>
     *     <li>The exception is propagated</li>
     *     <li>The Kafka offset is not acknowledged</li>
     *     <li>No normal processing logic is triggered</li>
     * </ul>
     *
     * <p>
     * This guarantees visibility of DLQ persistence failures and
     * prevents silent data loss.
     * </p>
     */
    @Test
    void paymentConsumeDlqShouldRethrowExceptionWhenS3Fails() throws IOException {
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
                .storeToS3(record, PAYMENT);

        // when -> then
        assertThrows(RuntimeException.class, () ->
                consumer.consumePaymentDlqMessages(record, acknowledgment)
        );
        // verify S3 attempt happened
        verify(dlqS3Service).storeToS3(record, PAYMENT);
        // verify no normal processing happened
        verifyNoInteractions(notificationRepository);
        verifyNoInteractions(emailService);
        verify(acknowledgment, never()).acknowledge();
    }

    /**
     * Verifies fail-fast behavior when DLQ persistence to S3 fails.
     *
     * <p>
     * DLQ messages represent already-failed records and must never
     * be silently dropped.
     * </p>
     *
     * <p>
     * Expected behavior:
     * </p>
     * <ul>
     *     <li>The S3 persistence attempt is made</li>
     *     <li>The exception is propagated</li>
     *     <li>The Kafka offset is not acknowledged</li>
     *     <li>No normal processing logic is triggered</li>
     * </ul>
     *
     * <p>
     * This guarantees visibility of DLQ persistence failures and
     * prevents silent data loss.
     * </p>
     */
    @Test
    void orderConsumeDlqShouldRethrowExceptionWhenS3Fails() throws IOException {
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
                .storeToS3(record, ORDER);

        // when -> then
        assertThrows(RuntimeException.class, () ->
                consumer.consumeOrderDlqMessages(record, acknowledgment)
        );
        // verify S3 attempt happened
        verify(dlqS3Service).storeToS3(record, ORDER);
        // DLQ path must not trigger normal flows
        verifyNoInteractions(notificationRepository);
        verifyNoInteractions(emailService);
        verify(acknowledgment, never()).acknowledge();
    }

    /**
     * Verifies successful handling of a payment-related Dead Letter Queue (DLQ) message.
     *
     * <p>
     * This test covers the <b>happy path</b> for the payment DLQ consumer.
     * When a {@link PaymentConfirmation} record is received from the payment DLQ topic:
     * </p>
     *
     * <ul>
     *     <li>The original Kafka {@link ConsumerRecord} is persisted to S3</li>
     *     <li>The event is categorized using {@link EventType#PAYMENT}</li>
     *     <li>The Kafka offset is acknowledged <b>only after</b> successful persistence</li>
     * </ul>
     *
     * <p>
     * No business processing (database writes, email delivery, validation)
     * is performed for DLQ records.
     * </p>
     *
     * <p>
     * This behavior ensures:
     * </p>
     * <ul>
     *     <li>Failed messages are durably stored for audit or replay</li>
     *     <li>No data loss for messages that exceeded retry limits</li>
     *     <li>Deterministic and minimal DLQ consumer logic</li>
     * </ul>
     */
    @Test
    void shouldStorePaymentDlqMessageToS3() throws Exception {
        // Given
        final PaymentConfirmation paymentConfirmationAvro = constructPaymentConfirmation();
        final ConsumerRecord<String, PaymentConfirmation> record =
                new ConsumerRecord<>("payment-dlq", 0, 10L, "key1", paymentConfirmationAvro);

        // When (should NOT throw)
        consumer.consumePaymentDlqMessages(record, acknowledgment);

        // Then
        verify(dlqS3Service).storeToS3(record, EventType.PAYMENT);
        verify(acknowledgment, atLeastOnce()).acknowledge();
    }

    /**
     * Verifies graceful handling of failures during payment DLQ persistence to S3.
     *
     * <p>
     * This test simulates an {@link IOException} occurring while attempting
     * to persist a payment DLQ record.
     * </p>
     *
     * <p>
     * Expected behavior:
     * </p>
     * <ul>
     *     <li>The persistence attempt to S3 is made</li>
     *     <li>The exception is caught and logged</li>
     *     <li>The exception does <b>not</b> propagate out of the listener</li>
     *     <li>The Kafka offset is <b>not acknowledged</b></li>
     * </ul>
     *
     * <p>
     * <b>Why swallow the exception?</b>
     * </p>
     * <ul>
     *     <li>DLQ consumers should not disrupt Kafka consumption</li>
     *     <li>Repeated retries for DLQ messages provide little value</li>
     *     <li>Operational visibility is preserved through logging and metrics</li>
     * </ul>
     *
     * <p>
     * This design prevents consumer crash loops while still ensuring
     * failed DLQ persistence is observable.
     * </p>
     */
    @Test
    void shouldNotFailWhenPaymentDlqS3PersistFails() throws Exception {
        // Given
        final ConsumerRecord<String, PaymentConfirmation> record =
                new ConsumerRecord<>("payment-dlq", 0, 10L, "key1", null);
        doThrow(new IOException("S3 down"))
                .when(dlqS3Service)
                .storeToS3(record, EventType.PAYMENT);

        // When / Then (no exception should escape)
        assertDoesNotThrow(() -> consumer.consumePaymentDlqMessages(record, acknowledgment));
        verify(dlqS3Service).storeToS3(record, EventType.PAYMENT);
        verify(acknowledgment, never()).acknowledge();
    }

    /**
     * Verifies successful handling of an order-related Dead Letter Queue (DLQ) message.
     *
     * <p>
     * This test validates that when an {@link OrderConfirmation} record is
     * received from the order DLQ topic:
     * </p>
     *
     * <ul>
     *     <li>The full Kafka {@link ConsumerRecord} is persisted to S3 unchanged</li>
     *     <li>The record is categorized under {@link EventType#ORDER}</li>
     *     <li>The Kafka offset is acknowledged after successful persistence</li>
     * </ul>
     *
     * <p>
     * Normal order processing logic (database writes, email notifications)
     * is intentionally skipped for DLQ records.
     * </p>
     *
     * <p>
     * This ensures safe retention of failed order events for
     * investigation or manual replay.
     * </p>
     */
    @Test
    void shouldStoreOrderDlqMessageToS3() throws Exception {
        // Given
        final CustomerResponse customerAvro = constructCustomer();
        final OrderConfirmation orderConfirmationAvro = constructOrderConfirmation(customerAvro);
        final ConsumerRecord<String, OrderConfirmation> record =
                new ConsumerRecord<>("order-dlq", 1, 5L, "key2", orderConfirmationAvro);

        // When
        consumer.consumeOrderDlqMessages(record, acknowledgment);

        // Then
        verify(dlqS3Service).storeToS3(record, EventType.ORDER);
        verify(acknowledgment, atLeastOnce()).acknowledge();
    }

    /**
     * Verifies graceful handling of failures during order DLQ persistence to S3.
     *
     * <p>
     * This test simulates an {@link IOException} thrown while storing
     * an order DLQ record.
     * </p>
     *
     * <p>
     * Expected behavior:
     * </p>
     * <ul>
     *     <li>The S3 persistence attempt is executed</li>
     *     <li>The exception is logged for operational visibility</li>
     *     <li>The exception does <b>not</b> escape the Kafka listener</li>
     *     <li>The Kafka offset remains unacknowledged</li>
     * </ul>
     *
     * <p>
     * This prevents Kafka listener container disruption while ensuring
     * that DLQ persistence failures are not silently ignored.
     * </p>
     */
    @Test
    void shouldNotFailWhenOrderDlqS3PersistFails() throws Exception {
        // Given
        final ConsumerRecord<String, OrderConfirmation> record =
                new ConsumerRecord<>("order-dlq", 1, 5L, "key2", null);
        doThrow(new IOException("S3 down"))
                .when(dlqS3Service)
                .storeToS3(record, EventType.ORDER);

        // When / Then
        assertDoesNotThrow(() -> consumer.consumeOrderDlqMessages(record, acknowledgment));
        verify(dlqS3Service).storeToS3(record, EventType.ORDER);
        verify(acknowledgment, never()).acknowledge();
    }

    /**
     * Constructs a valid {@link PaymentConfirmation} Avro record for test usage.
     *
     * <p>
     * The returned instance satisfies all required Avro schema constraints
     * and represents a realistic payment confirmation event.
     * </p>
     *
     * <p>
     * This helper method centralizes test data creation, ensuring:
     * </p>
     * <ul>
     *     <li>Consistency across multiple test cases</li>
     *     <li>Reduced duplication in test setup</li>
     *     <li>Clear intent when constructing payment-related test data</li>
     * </ul>
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
     * Constructs a valid {@link CustomerResponse} Avro record for testing.
     *
     * <p>
     * This record is used as a nested dependency for
     * {@link OrderConfirmation} events.
     * </p>
     *
     * <p>
     * Centralizing customer construction avoids duplication and
     * ensures all order-related tests use consistent customer data.
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
     * Constructs a valid {@link OrderConfirmation} Avro record using
     * the provided {@link CustomerResponse}.
     *
     * <p>
     * The returned object satisfies all Avro schema requirements and
     * represents a successfully confirmed order event.
     * </p>
     *
     * @param customer non-null customer record required by the Avro schema
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
