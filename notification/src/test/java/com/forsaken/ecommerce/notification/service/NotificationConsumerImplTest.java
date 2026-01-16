package com.forsaken.ecommerce.notification.service;

import com.forsaken.ecommerce.avro.CustomerResponse;
import com.forsaken.ecommerce.avro.OrderConfirmation;
import com.forsaken.ecommerce.avro.PaymentConfirmation;
import com.forsaken.ecommerce.notification.configs.kafka.IdempotencyScope;
import com.forsaken.ecommerce.notification.configs.kafka.IdempotencyStore;
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
 * <h2>Purpose</h2>
 *
 * <p>
 * This test suite validates the behavior of Kafka consumers responsible for
 * processing <b>payment</b> and <b>order</b> notification events in the
 * notification service.
 * </p>
 *
 * <p>
 * The tests are <b>pure unit tests</b> and intentionally avoid any dependency
 * on Kafka brokers, Redis, databases, or external services.
 * All collaborators are mocked.
 * </p>
 *
 * <h2>Key responsibilities under test</h2>
 *
 * <ul>
 *   <li>Correct handling of payment and order confirmation events</li>
 *   <li>Redis-based idempotency enforcement via {@link IdempotencyStore}</li>
 *   <li>Correct ordering of side effects (email → persistence → acknowledgment)</li>
 *   <li>Explicit Kafka acknowledgment behavior</li>
 *   <li>Failure propagation vs suppression semantics</li>
 * </ul>
 *
 * <h2>Idempotency behavior</h2>
 *
 * <p>
 * Kafka provides <b>at-least-once delivery</b>, which means duplicate events
 * are possible. To prevent duplicate email notifications, the consumer uses
 * a Redis-backed {@link IdempotencyStore}.
 * </p>
 *
 * <p>
 * In this test suite:
 * </p>
 * <ul>
 *   <li>{@code markIfNotProcessed(scope, eventId)} is explicitly stubbed</li>
 *   <li>Tests avoid generic matchers like {@code any()} for idempotency</li>
 *   <li>Each test controls whether processing should proceed or be skipped</li>
 * </ul>
 *
 * <p>
 * Redis behavior itself is <b>out of scope</b> and covered by
 * {@code IdempotencyStoreTest}.
 * </p>
 *
 * <h2>Processing order (Option A)</h2>
 *
 * <p>
 * The consumer follows a strict processing order:
 * </p>
 *
 * <ol>
 *   <li>Check idempotency</li>
 *   <li>Send email notification</li>
 *   <li>Persist notification record</li>
 *   <li>Acknowledge Kafka offset</li>
 * </ol>
 *
 * <p>
 * This ordering ensures that:
 * </p>
 * <ul>
 *   <li>No notification is persisted if email delivery fails</li>
 *   <li>Failures trigger Kafka retries or DLQ routing</li>
 *   <li>Duplicate events do not produce duplicate side effects</li>
 * </ul>
 *
 * <h2>Kafka acknowledgment strategy</h2>
 *
 * <p>
 * The consumer uses <b>manual acknowledgment</b>. These tests verify that:
 * </p>
 *
 * <ul>
 *   <li>Offsets are acknowledged only after successful processing</li>
 *   <li>Offsets are NOT acknowledged when processing fails</li>
 *   <li>Offsets are acknowledged immediately for tombstone (null) records</li>
 * </ul>
 *
 * <h2>Failure handling philosophy</h2>
 *
 * <ul>
 *   <li><b>Primary consumers (payment/order)</b>
 *     <ul>
 *       <li>Fail fast on processing errors</li>
 *       <li>Propagate exceptions</li>
 *       <li>Do not acknowledge offsets on failure</li>
 *     </ul>
 *   </li>
 *   <li><b>DLQ consumers</b>
 *     <ul>
 *       <li>Persist failed records to S3</li>
 *       <li>Never propagate exceptions</li>
 *       <li>Acknowledge offsets only after successful persistence</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <h2>Null (tombstone) record handling</h2>
 *
 * <p>
 * Kafka records with {@code null} payloads are treated as non-recoverable.
 * The consumer:
 * </p>
 *
 * <ul>
 *   <li>Skips all business processing</li>
 *   <li>Does not send emails or persist data</li>
 *   <li>Acknowledges the offset immediately</li>
 * </ul>
 *
 * <h2>Out of scope</h2>
 *
 * <ul>
 *   <li>Kafka retry configuration</li>
 *   <li>Redis TTL semantics</li>
 *   <li>Email formatting and templates</li>
 *   <li>Serialization / deserialization logic</li>
 * </ul>
 *
 * <p>
 * These concerns are covered by integration or component-level tests.
 * </p>
 *
 * <h2>Why this matters</h2>
 *
 * <p>
 * This test suite ensures correctness, safety, and operational predictability
 * of Kafka consumers by enforcing:
 * </p>
 *
 * <ul>
 *   <li>Exactly-once business effects</li>
 *   <li>At-least-once Kafka delivery semantics</li>
 *   <li>No silent data loss</li>
 *   <li>No duplicate notifications</li>
 * </ul>
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

    @Mock
    private IdempotencyStore idempotencyStore;

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
        when(idempotencyStore.markIfNotProcessed(
                eq(IdempotencyScope.PAYMENT),
                eq("ORD-100")
        )).thenReturn(true);
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
        when(idempotencyStore.markIfNotProcessed(
                eq(IdempotencyScope.ORDER),
                eq("ORD-200")
        )).thenReturn(true);
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
        when(idempotencyStore.markIfNotProcessed(
                eq(IdempotencyScope.PAYMENT),
                eq("ORD-100")
        )).thenReturn(true);
        final PaymentConfirmation paymentAvro = constructPaymentConfirmation();
        final ConsumerRecord<String, PaymentConfirmation> record =
                new ConsumerRecord<>("payment-topic", 0, 0L, "key", paymentAvro);
        doThrow(new RuntimeException("SMTP server down"))
                .when(emailService)
                .sendPaymentSuccessEmail(
                        eq("john@doe.com"),
                        eq("John Doe"),
                        eq(fromBytes(paymentAvro.getAmount())),
                        eq("ORD-100"),
                        eq(PaymentMethod.PAYPAL),
                        any(LocalDateTime.class)
                );

        // when + then
        assertThrows(RuntimeException.class, () ->
                consumer.consumePaymentSuccessNotifications(record, acknowledgment)
        );

        // email attempted
        verify(emailService).sendPaymentSuccessEmail(
                eq("john@doe.com"),
                eq("John Doe"),
                eq(fromBytes(paymentAvro.getAmount())),
                eq("ORD-100"),
                eq(PaymentMethod.PAYPAL),
                any(LocalDateTime.class)
        );

        // DB save must NOT happen
        verify(notificationRepository, never()).save(any());

        // offset must NOT be acknowledged
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
        when(idempotencyStore.markIfNotProcessed(
                eq(IdempotencyScope.ORDER),
                eq("ORD-200")
        )).thenReturn(true);
        final OrderConfirmation orderAvro = constructOrderConfirmation(constructCustomer());
        final ConsumerRecord<String, OrderConfirmation> record =
                new ConsumerRecord<>("order-topic", 0, 0L, "key", orderAvro);
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
        verify(emailService).sendOrderConfirmationEmail(
                eq("alice@smith.com"),
                eq("Alice Smith"),
                eq(fromBytes(orderAvro.getTotalAmount())),
                eq("ORD-200"),
                eq(List.of())
        );
        // DB save must NOT happen
        verify(notificationRepository, never()).save(any());
        // offset must NOT be acknowledged
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
     * Verifies that an order-related Dead Letter Queue (DLQ) message
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
     *     <li>The record is categorized using {@link EventType#ORDER}</li>
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
     * Verifies fail-safe behavior when DLQ persistence to S3 fails.
     *
     * <p>
     * DLQ messages represent records that have already failed normal processing.
     * DLQ consumers must be resilient and should not fail-fast, as rethrowing
     * exceptions would lead to repeated retries and consumer crash loops.
     * </p>
     *
     * <p>
     * Expected behavior:
     * </p>
     * <ul>
     *     <li>The S3 persistence attempt is made</li>
     *     <li>The exception is caught and logged</li>
     *     <li>The exception does <b>not</b> propagate out of the listener</li>
     *     <li>The Kafka offset is <b>not acknowledged</b></li>
     *     <li>No normal processing logic is triggered</li>
     * </ul>
     *
     * <p>
     * This design preserves operational visibility of DLQ persistence failures
     * through logging while preventing Kafka consumer disruption and infinite
     * retry loops.
     * </p>
     */
    @Test
    void paymentConsumeDlqShouldNotThrowExceptionWhenS3Fails() throws IOException {
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
        doThrow(new IOException("S3 unavailable"))
                .when(dlqS3Service)
                .storeToS3(record, PAYMENT);

        // when -> then
        assertDoesNotThrow(() ->
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
     * Verifies fail-safe behavior when DLQ persistence to S3 fails.
     *
     * <p>
     * DLQ messages represent already-failed records. DLQ consumers must be
     * resilient and must <b>not</b> fail-fast, as repeated retries provide
     * little value and can cause consumer crash loops.
     * </p>
     *
     * <p>
     * Expected behavior:
     * </p>
     * <ul>
     *     <li>The S3 persistence attempt is made</li>
     *     <li>The exception is caught and logged</li>
     *     <li>The exception does <b>not</b> propagate out of the listener</li>
     *     <li>The Kafka offset is <b>not acknowledged</b></li>
     *     <li>No normal processing logic is triggered</li>
     * </ul>
     *
     * <p>
     * This design prevents Kafka consumer disruption while preserving
     * operational visibility through logs and metrics, ensuring that
     * failed DLQ persistence remains observable without crashing the consumer.
     * </p>
     */
    @Test
    void orderConsumeDlqShouldNotThrowExceptionWhenS3Fails() throws IOException {
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
        doThrow(new IOException("S3 write failed"))
                .when(dlqS3Service)
                .storeToS3(record, ORDER);

        // when -> then
        assertDoesNotThrow(() ->
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
     * <p>
     * Expected behavior:
     * <ul>
     *   <li>The persistence attempt to S3 is made</li>
     *   <li>The exception is caught and logged</li>
     *   <li>The exception does <b>not</b> propagate out of the listener</li>
     *   <li>The Kafka offset is <b>not acknowledged</b></li>
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
     *     <li>The exception is caught and does <b>not</b> propagate beyond the Kafka listener method</li>
     *     <li>The Kafka offset remains unacknowledged so that the record can be retried according to the listener container configuration</li>
     * </ul>
     *
     * <p>
     * This prevents Kafka listener container disruption while making the failure visible via logging and offset management, rather than by propagating the exception.
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
