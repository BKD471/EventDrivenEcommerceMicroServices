package com.forsaken.ecommerce.notification.configs.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link KafkaProducerConfigurations}.
 *
 * <p>
 * These tests verify that Kafka producer-related infrastructure beans
 * are created and configured correctly <strong>without starting a Spring context</strong>.
 * </p>
 *
 * <p>
 * Scope of this test:
 * </p>
 * <ul>
 *   <li>ObjectMapper configuration (Java Time support)</li>
 *   <li>Kafka ProducerFactory configuration</li>
 *   <li>KafkaTemplate creation</li>
 *   <li>Fail-fast behavior when mandatory Kafka properties are missing</li>
 * </ul>
 *
 * <p>
 * This is a <strong>pure unit test</strong>:
 * </p>
 * <ul>
 *   <li>No Kafka broker is required</li>
 *   <li>No Schema Registry is required</li>
 *   <li>No Spring Boot context is loaded</li>
 * </ul>
 *
 * <p>
 * Mockito is used only to mock {@link KafkaProperties};
 * real configuration values are asserted wherever possible.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class KafkaProducerConfigurationsTest {

    @Mock
    private KafkaProperties kafkaProperties;

    private KafkaProducerConfigurations configuration;


    @BeforeEach
    void setUp() {
        configuration = new KafkaProducerConfigurations(kafkaProperties);
    }

    /**
     * Verifies that the ObjectMapper is configured with Java Time support.
     *
     * <p>
     * Specifically ensures that {@code JavaTimeModule} is registered,
     * allowing proper serialization/deserialization of Java 8+ date/time types.
     * </p>
     */
    @Test
    void shouldCreateConfiguredObjectMapper() {
        // when
        final ObjectMapper mapper = configuration.objectMapper();

        // then
        assertThat(mapper.getRegisteredModuleIds())
                .contains("jackson-datatype-jsr310");
    }

    /**
     * Verifies that Java Time objects are serialized in ISO-8601 format
     * and not as numeric timestamps.
     *
     * <p>
     * This is a behavior-based test that validates the observable outcome
     * of ObjectMapper configuration rather than its internal state.
     * </p>
     */
    @Test
    void shouldSerializeJavaTimeCorrectly() throws Exception {
        // given
        final ObjectMapper mapper = configuration.objectMapper();
        final LocalDateTime now =
                LocalDateTime.of(2025, 1, 1, 10, 0);

        // when
        final String json = mapper.writeValueAsString(now);

        // then
        assertThat(json).isEqualTo("\"2025-01-01T10:00:00\"");
    }

    /**
     * Verifies that the Kafka {@link ProducerFactory} is created with the
     * expected producer configuration properties.
     *
     * <p>
     * This test asserts:
     * </p>
     * <ul>
     *   <li>Bootstrap servers are set correctly</li>
     *   <li>Key serializer is {@link StringSerializer}</li>
     *   <li>Value serializer is {@link KafkaAvroSerializer}</li>
     *   <li>Schema Registry URL is configured</li>
     * </ul>
     *
     * <p>
     * No Mockito argument matchers are used here to ensure real configuration
     * values are validated.
     * </p>
     */
    @Test
    void shouldCreateProducerFactoryWithCorrectKafkaConfigs() {
        // given
        final List<String> bootstrapServers = List.of("localhost:9092", "localhost:9093");
        final String schemaRegistryUrl = "http://localhost:8081";
        when(kafkaProperties.bootstrapServers()).thenReturn(bootstrapServers);
        when(kafkaProperties.schemaRegistryUrl()).thenReturn(schemaRegistryUrl);

        // when
        final ProducerFactory<String, SpecificRecordBase> factory =
                configuration.producerFactory();

        // then
        assertThat(factory).isInstanceOf(DefaultKafkaProducerFactory.class);
        final DefaultKafkaProducerFactory<?, ?> defaultFactory =
                (DefaultKafkaProducerFactory<?, ?>) factory;
        final Map<String, Object> configs = defaultFactory.getConfigurationProperties();
        assertThat(configs)
                .containsEntry(
                        ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                        bootstrapServers
                )
                .containsEntry(
                        ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                        StringSerializer.class
                )
                .containsEntry(
                        ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                        KafkaAvroSerializer.class
                )
                .containsEntry(
                        "schema.registry.url",
                        schemaRegistryUrl
                );
    }

    /**
     * Verifies that a {@link KafkaTemplate} can be created successfully
     * using the configured {@link ProducerFactory}.
     *
     * <p>
     * This ensures that downstream components (e.g. DLQ publishers)
     * can safely inject and use the KafkaTemplate.
     * </p>
     */
    @Test
    void shouldCreateKafkaTemplateUsingProducerFactory() {
        // given
        when(kafkaProperties.bootstrapServers())
                .thenReturn(List.of("localhost:9092"));
        when(kafkaProperties.schemaRegistryUrl())
                .thenReturn("http://localhost:8081");

        // when
        final KafkaTemplate<String, SpecificRecordBase> template =
                configuration.kafkaTemplate();

        // then
        assertThat(template).isNotNull();
        assertThat(template.getProducerFactory()).isNotNull();
    }

    /**
     * Verifies that producer configuration fails fast when mandatory Kafka
     * properties are missing.
     *
     * <p>
     * Kafka requires {@code bootstrap.servers} and (when using Avro)
     * {@code schema.registry.url}. Supplying {@code null} values must result
     * in an immediate failure rather than a delayed runtime error.
     * </p>
     *
     * <p>
     * This test intentionally asserts a {@link NullPointerException} to
     * document and lock in this fail-fast behavior.
     * </p>
     */
    @Test
    void shouldFailWhenMandatoryKafkaPropertiesAreMissing() {
        // given
        when(kafkaProperties.bootstrapServers()).thenReturn(null);
        when(kafkaProperties.schemaRegistryUrl()).thenReturn(null);

        // then
        assertThatThrownBy(() -> configuration.producerFactory())
                .isInstanceOf(NullPointerException.class);
    }
}
