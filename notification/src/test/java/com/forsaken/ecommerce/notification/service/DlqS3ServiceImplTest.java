package com.forsaken.ecommerce.notification.service;

import com.forsaken.ecommerce.avro.PaymentConfirmation;
import com.forsaken.ecommerce.avro.PaymentMethod;
import com.forsaken.ecommerce.notification.configs.s3.DlqS3Properties;
import com.forsaken.ecommerce.notification.models.EventType;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DlqS3ServiceImpl}.
 *
 * <p>
 * This test suite verifies the behavior of the DLQ S3 service in isolation,
 * using Mockito to mock AWS S3 interactions and configuration properties.
 * </p>
 *
 * <p>
 * The tests focus on:
 * </p>
 * <ul>
 *   <li>Correct S3 key prefix selection based on {@link EventType}</li>
 *   <li>Proper serialization of Avro and non-Avro Kafka records</li>
 *   <li>Correct handling of S3 read, write, list, and delete operations</li>
 *   <li>Propagation of exceptions when underlying S3 operations fail</li>
 * </ul>
 *
 * <p>
 * <b>Testing principles followed:</b>
 * </p>
 * <ul>
 *   <li>No Spring context is loaded (pure unit tests)</li>
 *   <li>Strict Mockito stubbing is enforced</li>
 *   <li>{@code any()} is avoided in positive test paths</li>
 *   <li>Real {@link ConsumerRecord} instances are used</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class DlqS3ServiceImplTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private DlqS3Properties properties;

    @InjectMocks
    private DlqS3ServiceImpl service;

    private static final String BUCKET = "dlq-bucket";
    private static final String PAYMENT_PREFIX = "dlq/payment";
    private static final String ORDER_PREFIX = "dlq/order";

    @BeforeEach
    void setup() {
        when(properties.bucket()).thenReturn(BUCKET);
    }

    /**
     * Verifies that Avro-based Kafka records are correctly persisted to S3
     * for both {@link EventType#PAYMENT} and {@link EventType#ORDER}.
     *
     * <p>
     * This test is parameterized to ensure that:
     * </p>
     * <ul>
     *   <li>The correct S3 prefix is selected based on the event type</li>
     *   <li>The generated S3 object key starts with the expected prefix</li>
     *   <li>The stored JSON payload contains Kafka metadata such as
     *       topic, partition, and offset</li>
     * </ul>
     *
     * <p>
     * The test uses Avro {@link PaymentConfirmation} records to validate
     * schema-aware JSON serialization.
     * </p>
     *
     * @param eventType      the logical event type (payment or order)
     * @param expectedPrefix the expected S3 prefix for the event type
     * @param topic          the Kafka topic name used in the record
     */
    @ParameterizedTest
    @MethodSource("eventTypeProvider")
    void shouldStoreAvroRecordToS3_forPaymentAndOrder(
            EventType eventType,
            String expectedPrefix,
            String topic
    ) throws Exception {
        when(properties.bucket()).thenReturn(BUCKET);
        switch (eventType) {
            case PAYMENT -> when(properties.paymentPrefix()).thenReturn(expectedPrefix);
            case ORDER -> when(properties.orderPrefix()).thenReturn(expectedPrefix);
        }
        final PaymentConfirmation avro =
                PaymentConfirmation.newBuilder()
                        .setOrderReference("ORD-1")
                        .setAmount(ByteBuffer.wrap("100".getBytes()))
                        .setPaymentMethod(PaymentMethod.CREDIT_CARD)
                        .setCustomerFirstname("TestFirstName")
                        .setCustomerLastname("TestLastName")
                        .setCustomerEmail("test@domain.com")
                        .setPaymentDate(Instant.now())
                        .build();
        final ConsumerRecord<String, PaymentConfirmation> record =
                new ConsumerRecord<>(topic, 1, 10L, "key1", avro);
        final ArgumentCaptor<PutObjectRequest> requestCaptor =
                ArgumentCaptor.forClass(PutObjectRequest.class);
        final ArgumentCaptor<RequestBody> bodyCaptor =
                ArgumentCaptor.forClass(RequestBody.class);

        // When
        service.storeToS3(record, eventType);

        // Then
        verify(s3Client).putObject(requestCaptor.capture(), bodyCaptor.capture());
        final PutObjectRequest request = requestCaptor.getValue();
        final String body = new String(
                bodyCaptor.getValue()
                        .contentStreamProvider()
                        .newStream()
                        .readAllBytes(),
                StandardCharsets.UTF_8
        );
        assertEquals(BUCKET, request.bucket());
        assertTrue(request.key().startsWith(expectedPrefix));
        assertEquals("application/json", request.contentType());
        assertTrue(body.contains("\"topic\":\"" + topic + "\""));
        assertTrue(body.contains("\"partition\":1"));
        assertTrue(body.contains("\"offset\":10"));
        assertTrue(body.contains("\"valueJson\""));
    }

    /**
     * Supplies event-type-specific arguments for parameterized DLQ storage tests.
     *
     * <p>
     * Each argument set defines:
     * </p>
     * <ul>
     *   <li>The {@link EventType}</li>
     *   <li>The expected S3 prefix</li>
     *   <li>The Kafka topic name</li>
     * </ul>
     *
     * @return a stream of arguments covering supported event types
     */
    private static Stream<Arguments> eventTypeProvider() {
        return Stream.of(
                Arguments.of(EventType.PAYMENT, PAYMENT_PREFIX, "payment-topic"),
                Arguments.of(EventType.ORDER, ORDER_PREFIX, "order-topic")
        );
    }

    /**
     * Verifies that non-Avro Kafka record values are serialized using Jackson
     * and successfully stored in S3.
     *
     * <p>
     * This test ensures that the service gracefully handles generic payloads
     * (e.g. maps or plain objects) in addition to Avro records.
     * </p>
     */
    @Test
    void shouldStoreNonAvroRecordToS3() throws Exception {
        // Given
        final Map<String, Object> value = Map.of("id", 1, "status", "FAILED");
        final ConsumerRecord<String, Object> record =
                new ConsumerRecord<>("order-topic", 0, 5L, "k1", value);
        final ArgumentCaptor<PutObjectRequest> requestCaptor =
                ArgumentCaptor.forClass(PutObjectRequest.class);

        // When
        service.storeToS3(record, EventType.ORDER);

        // Then
        verify(s3Client).putObject(requestCaptor.capture(), any(RequestBody.class));
        assertEquals(BUCKET, requestCaptor.getValue().bucket());
    }

    /**
     * Verifies that an exception is propagated when the S3 upload operation fails.
     *
     * <p>
     * This ensures that callers are made aware of persistence failures
     * and can decide whether to retry, escalate, or log the error.
     * </p>
     */
    @Test
    void shouldThrowExceptionWhenS3UploadFails() {
        // Given
        final ConsumerRecord<String, String> record =
                new ConsumerRecord<>("topic", 0, 1L, "k", "value");
        doThrow(RuntimeException.class)
                .when(s3Client)
                .putObject(any(PutObjectRequest.class), any(RequestBody.class));

        // When Then
        assertThrows(RuntimeException.class,
                () -> service.storeToS3(record, EventType.PAYMENT));
    }

    /**
     * Verifies that all S3 object keys under a given prefix are returned
     * when matching DLQ records exist.
     */
    @Test
    void shouldReturnKeysWhenObjectsExist() {
        // Given
        final ListObjectsV2Response response =
                ListObjectsV2Response.builder()
                        .contents(
                                List.of(
                                        S3Object.builder().key("k1").build(),
                                        S3Object.builder().key("k2").build()
                                )
                        )
                        .build();
        when(s3Client.listObjectsV2(
                ListObjectsV2Request.builder()
                        .bucket(BUCKET)
                        .prefix(PAYMENT_PREFIX)
                        .build()
        )).thenReturn(response);

        // When
        final List<String> keys = service.listKeys(PAYMENT_PREFIX);

        // Then
        assertEquals(2, keys.size());
        assertTrue(keys.contains("k1"));
        assertTrue(keys.contains("k2"));
    }

    /**
     * Verifies that an empty list is returned when no DLQ records
     * exist under the given S3 prefix.
     */
    @Test
    void shouldReturnEmptyListWhenNoObjectsFound() {
        // Given
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(ListObjectsV2Response.builder().build());

        // When
        final List<String> keys = service.listKeys(PAYMENT_PREFIX);

        // Then
        assertTrue(keys.isEmpty());
    }

    /**
     * Verifies that a stored DLQ record can be loaded from S3
     * and its JSON content returned as a string.
     */
    @Test
    void shouldLoadObjectContentFromS3() {
        // Given
        final String json = "{\"test\":true}";
        when(s3Client.getObjectAsBytes(
                GetObjectRequest.builder()
                        .bucket(BUCKET)
                        .key("key1")
                        .build()
        )).thenReturn(ResponseBytes.fromByteArray(
                GetObjectResponse.builder().build(),
                json.getBytes(StandardCharsets.UTF_8)
        ));

        // When
        final String result = service.load("key1");

        // Then
        assertEquals(json, result);
    }

    /**
     * Verifies that an exception is thrown when loading a DLQ record
     * from S3 fails.
     */
    @Test
    void shouldThrowExceptionWhenLoadFails() {
        // When
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenThrow(RuntimeException.class);

        // Then
        assertThrows(RuntimeException.class,
                () -> service.load("key"));
    }

    /**
     * Verifies that a DLQ record is deleted from Amazon S3 when the
     * {@link DlqS3ServiceImpl#delete(String)} method is invoked.
     *
     * <p>
     * This test ensures that the service constructs and sends a
     * {@link DeleteObjectRequest} with the correct bucket name and
     * object key to the underlying {@link S3Client}.
     * </p>
     *
     * <p>
     * No value is returned by the delete operation; therefore, the test
     * validates behavior by verifying the interaction with the S3 client.
     * </p>
     */
    @Test
    void shouldDeleteObjectFromS3() {
        // When
        service.delete("key1");

        // Then
        verify(s3Client).deleteObject(
                DeleteObjectRequest.builder()
                        .bucket(BUCKET)
                        .key("key1")
                        .build()
        );
    }

    /**
     * Verifies that an exception is propagated when deleting a DLQ
     * record from S3 fails.
     */
    @Test
    void shouldThrowExceptionWhenDeleteFails() {
        // When
        doThrow(RuntimeException.class)
                .when(s3Client)
                .deleteObject(any(DeleteObjectRequest.class));

        // Then
        assertThrows(RuntimeException.class,
                () -> service.delete("key"));
    }
}
