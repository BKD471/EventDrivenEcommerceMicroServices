package com.forsaken.ecommerce.notification.mapper;

import com.forsaken.ecommerce.avro.CustomerResponse;
import com.forsaken.ecommerce.avro.OrderConfirmation;
import com.forsaken.ecommerce.avro.PaymentConfirmation;
import org.apache.avro.Schema;
import org.apache.avro.specific.SpecificRecordBase;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link AvroJsonConverter}.
 *
 * <p>
 * This test suite validates schema-aware conversion between Avro
 * {@link SpecificRecordBase} instances and their JSON representations.
 * </p>
 *
 * <p>
 * The tests cover:
 * </p>
 * <ul>
 *   <li>Avro → JSON conversion</li>
 *   <li>JSON → Avro deserialization</li>
 *   <li>Round-trip safety (Avro → JSON → Avro)</li>
 *   <li>Failure scenarios such as invalid JSON and schema mismatch</li>
 * </ul>
 *
 * <p>
 * Multiple Avro record types are exercised using parameterized tests
 * to ensure the converter behaves consistently across different schemas
 * (e.g. {@link PaymentConfirmation} and {@link OrderConfirmation}).
 * </p>
 *
 * <p>
 * These are pure unit tests with no external dependencies such as Kafka,
 * Spring, or Jackson. All assertions are schema-based rather than
 * string-format dependent.
 * </p>
 */
class AvroJsonConverterTest {


    /**
     * Verifies that {@link AvroJsonConverter} cannot be instantiated.
     *
     * <p>
     * {@code AvroJsonConverter} is a utility class and is not intended to be
     * instantiated. Its private constructor explicitly throws an
     * {@link UnsupportedOperationException} to enforce this contract.
     * </p>
     *
     * <p>
     * Since the constructor is {@code private}, reflection is used to access it.
     * Any exception thrown by the constructor is wrapped in an
     * {@link InvocationTargetException}, so the test asserts on the underlying
     * cause.
     * </p>
     *
     * <p>
     * This test ensures:
     * </p>
     * <ul>
     *   <li>The constructor is not usable for instantiation</li>
     *   <li>An {@link UnsupportedOperationException} is thrown</li>
     *   <li>The exception message clearly communicates the intent</li>
     * </ul>
     */
    @Test
    void shouldNotAllowInstantiation() throws Exception {
        // Given
        final Constructor<AvroJsonConverter> constructor =
                AvroJsonConverter.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        // When / Then
        final InvocationTargetException exception =
                assertThrows(InvocationTargetException.class, constructor::newInstance);
        assertTrue(exception.getCause() instanceof UnsupportedOperationException);
        assertEquals(
                "Utility class cannot be instantiated",
                exception.getCause().getMessage()
        );
    }


    /**
     * Verifies that an Avro record can be converted to JSON and deserialized
     * back using the same schema.
     *
     * <p>
     * This test intentionally avoids asserting on the JSON string structure,
     * as Apache Avro does not guarantee field ordering or flat representations
     * in its JSON encoding.
     * </p>
     *
     * <p>
     * Successful round-trip deserialization proves that the generated JSON
     * is schema-compliant and suitable for persistence or replay.
     * </p>
     */
    @ParameterizedTest
    @MethodSource("avroRecordProvider")
    void shouldConvertAvroToJson(
            final SpecificRecordBase avro
    ) throws Exception {
        // When
        final String json = AvroJsonConverter.avroToJson(avro);

        // Then
        final SpecificRecordBase restored =
                AvroJsonConverter.jsonToAvro(json, avro.getSchema());
        assertEquals(
                avro.getSchema().getFullName(),
                restored.getSchema().getFullName()
        );
    }

    /**
     * Verifies that a JSON representation produced from an Avro record
     * can be deserialized back into an Avro object using the same schema.
     *
     * <p>
     * This test validates correct JSON → Avro deserialization for multiple
     * Avro record types.
     * </p>
     */
    @ParameterizedTest
    @MethodSource("avroRecordProvider")
    void shouldConvertJsonToAvro(
            final SpecificRecordBase avro,
            final String ignored
    ) throws Exception {
        // Given
        final String json = AvroJsonConverter.avroToJson(avro);
        final Schema schema = avro.getSchema();

        // When
        final SpecificRecordBase result =
                AvroJsonConverter.jsonToAvro(json, schema);

        // Then
        assertNotNull(result);
        assertEquals(avro.getSchema().getFullName(), result.getSchema().getFullName());
    }

    /**
     * Verifies round-trip safety of the converter.
     *
     * <p>
     * An Avro record is serialized to JSON and then deserialized back
     * into an Avro record using the same schema.
     * </p>
     *
     * <p>
     * This is the strongest correctness guarantee for the converter and
     * ensures it is safe to use for DLQ storage, auditing, and replay.
     * </p>
     */
    @ParameterizedTest
    @MethodSource("avroRecordProvider")
    void shouldSupportRoundTripConversion(
            final SpecificRecordBase avro,
            final String ignored
    ) throws Exception {
        // When
        final String json = AvroJsonConverter.avroToJson(avro);
        final SpecificRecordBase restored =
                AvroJsonConverter.jsonToAvro(json, avro.getSchema());

        // Then
        assertEquals(avro.getSchema().getFullName(), restored.getSchema().getFullName());
    }

    /**
     * Supplies valid Avro records for parameterized conversion tests.
     *
     * <p>
     * Each argument represents a fully populated {@link SpecificRecordBase}
     * instance with a valid schema.
     * </p>
     *
     * @return a stream of Avro records used for positive conversion tests
     */
    private static Stream<Arguments> avroRecordProvider() {
        return Stream.of(
                Arguments.of(
                        constructPaymentConfirmation(),
                        "\"orderReference\":\"ORD-PAY\""
                ),
                Arguments.of(
                        constructOrderConfirmation(constructCustomer()),
                        "\"orderReference\":\"ORD-ORDER\""
                )
        );
    }

    /**
     * Verifies that deserialization fails when the input JSON is malformed.
     *
     * <p>
     * This test ensures that invalid JSON input results in an
     * {@link IOException}, indicating a parsing failure before schema
     * resolution occurs.
     * </p>
     */
    @Test
    void shouldFailWhenJsonIsInvalid() {
        // Given
        final String invalidJson = "{ not-valid-json }";
        final Schema schema = PaymentConfirmation.getClassSchema();

        // Then
        assertThrows(IOException.class,
                () -> AvroJsonConverter.jsonToAvro(invalidJson, schema));
    }

    /**
     * Verifies that deserialization fails when the provided Avro schema
     * does not match the structure of the JSON.
     *
     * <p>
     * Apache Avro throws {@link org.apache.avro.AvroTypeException} when
     * JSON content cannot be resolved against the given schema.
     * </p>
     *
     * <p>
     * This test validates that such schema mismatches are surfaced correctly
     * and are not silently ignored.
     * </p>
     */
    @ParameterizedTest
    @MethodSource("schemaMismatchProvider")
    void shouldFailWhenSchemaDoesNotMatchJson(final SpecificRecordBase avro) throws Exception {
        // Given
        final String json = AvroJsonConverter.avroToJson(avro);
        final Schema wrongSchema = Schema.create(Schema.Type.STRING);

        // Then
        assertThrows(org.apache.avro.AvroTypeException.class,
                () -> AvroJsonConverter.jsonToAvro(json, wrongSchema));
    }

    /**
     * Supplies Avro records for schema-mismatch failure tests.
     *
     * <p>
     * Each record is serialized to JSON and then deserialized using an
     * incompatible schema to verify correct failure behavior.
     * </p>
     *
     * @return a stream of Avro records used for negative schema tests
     */
    private static Stream<Arguments> schemaMismatchProvider() {
        return Stream.of(
                Arguments.of(
                        constructPaymentConfirmation()
                ),
                Arguments.of(
                        constructOrderConfirmation(constructCustomer())
                )
        );
    }


    /**
     * Constructs a valid Avro {@link PaymentConfirmation}
     * for use in mapping tests.
     */
    private static PaymentConfirmation constructPaymentConfirmation() {
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
     * Constructs a valid Avro {@link CustomerResponse}
     * consistent with the Avro schema constraints.
     */
    private static CustomerResponse constructCustomer() {
        return CustomerResponse.newBuilder()
                .setId("cust-1")
                .setFirstname("Alice")
                .setLastname("Smith")
                .setEmail("alice@smith.com")
                .build();
    }

    /**
     * Constructs a valid Avro {@link OrderConfirmation}
     * with a required {@link CustomerResponse}.
     */
    private static OrderConfirmation constructOrderConfirmation(final CustomerResponse customer) {
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
