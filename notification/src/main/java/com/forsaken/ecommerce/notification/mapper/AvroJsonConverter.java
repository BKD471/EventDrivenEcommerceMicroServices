package com.forsaken.ecommerce.notification.mapper;

import org.apache.avro.Schema;
import org.apache.avro.io.Encoder;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.specific.SpecificRecordBase;
import org.apache.avro.specific.SpecificDatumWriter;
import org.apache.avro.io.Decoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.specific.SpecificDatumReader;


import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Utility class for converting between Avro {@link SpecificRecordBase} objects
 * and their JSON representations.
 *
 * <p>
 * This converter uses Apache Avro's JSON encoder/decoder to ensure that the
 * JSON produced and consumed strictly adheres to the provided Avro {@link Schema}.
 * It does <b>not</b> perform generic Jackson-based serialization; instead,
 * it relies on Avro's schema-aware serialization mechanisms.
 * </p>
 *
 * <p>
 * Typical use cases include:
 * </p>
 * <ul>
 *   <li>Logging Avro records in human-readable JSON format</li>
 *   <li>Debugging Kafka messages serialized with Avro</li>
 *   <li>Converting JSON payloads back into Avro objects for testing or replay</li>
 * </ul>
 *
 * <p><b>Important Notes:</b></p>
 * <ul>
 *   <li>The JSON must conform exactly to the provided Avro schema.</li>
 *   <li>Default values defined in the schema are applied during deserialization.</li>
 *   <li>This utility works only with {@link SpecificRecordBase} (not GenericRecord).</li>
 * </ul>
 */
public class AvroJsonConverter {

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private AvroJsonConverter() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Converts a given Avro {@link SpecificRecordBase} instance into its
     * JSON representation using the record's schema.
     *
     * <p>
     * The generated JSON is schema-compliant and can be deserialized back
     * into the same Avro type using {@link #jsonToAvro(String, Schema)}.
     * </p>
     *
     * @param avro the Avro specific record to convert; must not be {@code null}
     * @return a JSON string representation of the Avro record
     * @throws IOException if serialization fails
     */
    public static String avroToJson(final SpecificRecordBase avro) throws IOException {
        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
            final Encoder jsonEncoder = EncoderFactory.get().jsonEncoder(avro.getSchema(), byteArrayOutputStream);
            final DatumWriter<SpecificRecordBase> specificRecordBaseDatumWriter = new SpecificDatumWriter<>(avro.getSchema());
            specificRecordBaseDatumWriter.write(avro, jsonEncoder);
            jsonEncoder.flush();
            return byteArrayOutputStream.toString(StandardCharsets.UTF_8);
        }
    }

    /**
     * Converts a JSON string into an Avro {@link SpecificRecordBase} instance
     * using the provided Avro {@link Schema}.
     *
     * <p>
     * This method performs schema-aware deserialization using Apache Avro’s
     * JSON decoder. The input JSON must strictly conform to the supplied schema.
     * Any fields missing from the JSON must have default values defined in the
     * schema; otherwise, deserialization will fail.
     * </p>
     *
     * <p>
     * The returned object type is inferred from the generic type parameter {@code T}.
     * Callers are responsible for ensuring that the provided schema corresponds
     * to the expected Avro specific record type.
     * </p>
     *
     * <p><b>Typical use cases include:</b></p>
     * <ul>
     *   <li>Reconstructing Avro records from JSON stored in a DLQ or audit store</li>
     *   <li>Replaying failed Kafka messages</li>
     *   <li>Testing and debugging Avro-based message flows</li>
     * </ul>
     *
     * <p><b>Important:</b></p>
     * <ul>
     *   <li>This method supports only {@link SpecificRecordBase} implementations.</li>
     *   <li>Type mismatches between the schema and the expected record class may
     *       result in runtime deserialization errors.</li>
     * </ul>
     *
     * @param <T>    the specific Avro record type
     * @param json   the JSON representation of the Avro record
     * @param schema the Avro schema used to deserialize the JSON
     * @return the deserialized Avro specific record
     * @throws IOException if deserialization fails due to invalid JSON,
     *                     schema mismatch, or missing required fields
     */
    public static <T extends SpecificRecordBase> T jsonToAvro(
            final String json,
            final Schema schema
    ) throws IOException {
        final SpecificDatumReader<T> reader =
                new SpecificDatumReader<>(schema);
        final Decoder decoder = DecoderFactory.get().jsonDecoder(schema, json);
        return reader.read(null, decoder);
    }
}