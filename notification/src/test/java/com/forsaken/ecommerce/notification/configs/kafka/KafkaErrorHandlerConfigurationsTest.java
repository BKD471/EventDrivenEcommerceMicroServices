package com.forsaken.ecommerce.notification.configs.kafka;


import com.forsaken.ecommerce.notification.models.EventType;
import nl.altindag.log.LogCaptor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link KafkaErrorHandlerConfigurations}.
 *
 * <p>
 * This test suite verifies the configuration and behavior of Kafka error handling,
 * including:
 * </p>
 * <ul>
 *     <li>Creation of the Avro {@link KafkaTemplate}</li>
 *     <li>Initialization of {@link DefaultErrorHandler}</li>
 *     <li>Correct routing of failed records to Payment and Order DLQ topics</li>
 *     <li>Fail-fast behavior for unknown or null source topics</li>
 *     <li>Invocation and logging of retry listeners during retry attempts</li>
 * </ul>
 *
 * <p>
 * The tests intentionally avoid relying on Spring Kafka internals or reflection.
 * Instead, they validate observable behavior by invoking public configuration
 * methods and helper methods exposed for testability.
 * </p>
 *
 * <p>
 * Kafka runtime infrastructure is mocked to ensure these tests remain fast,
 * deterministic, and focused purely on configuration logic.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class KafkaErrorHandlerConfigurationsTest {

    @Mock
    private KafkaProperties kafkaProperties;

    @Mock
    private KafkaDlqProperties kafkaDlqProperties;

    private KafkaErrorHandlerConfigurations configuration;

    @BeforeEach
    void setup() {
        configuration =
                new KafkaErrorHandlerConfigurations(kafkaProperties, kafkaDlqProperties);
    }

    /**
     * Verifies that an Avro-based {@link KafkaTemplate} can be created
     * when the required Kafka and Schema Registry properties are provided.
     */
    @Test
    void shouldCreateAvroKafkaTemplate() {
        // Given
        when(kafkaProperties.bootstrapServers())
                .thenReturn(List.of("localhost:9092"));
        when(kafkaProperties.schemaRegistryUrl())
                .thenReturn("http://localhost:8081");

        // When
        final KafkaTemplate<String, Object> template =
                configuration.avroKafkaTemplate();

        // Then
        assertNotNull(template);
    }

    /**
     * Verifies that a {@link DefaultErrorHandler} is successfully created
     * with the configured retry and backoff settings.
     */
    @Test
    void shouldCreateDefaultErrorHandler() {
        // Given
        when(kafkaProperties.schemaRegistryUrl()).thenReturn("http://localhost:8081");
        when(kafkaDlqProperties.maxAttempts()).thenReturn(3);
        when(kafkaDlqProperties.backOffInterval()).thenReturn(Math.toIntExact(1_000L));
        when(kafkaDlqProperties.multiplier()).thenReturn(2.0);
        when(kafkaDlqProperties.maxInterval()).thenReturn(Math.toIntExact(10_000L));

        // When
        final KafkaTemplate<String, Object> template =
                configuration.avroKafkaTemplate();
        final DefaultErrorHandler handler =
                configuration.errorHandler(template);

        // Then
        assertNotNull(handler);
    }

    /**
     * Verifies that records originating from Payment and Order topics
     * are routed to the correct Dead Letter Topic (DLQ) based on the
     * resolved {@link EventType}.
     *
     * <p>
     * This test covers the logic used by the {@code DeadLetterPublishingRecoverer}
     * to determine the destination {@link TopicPartition}.
     * </p>
     */
    @ParameterizedTest(name = "topic={0} should route serialization exception to correct DLQ={2}")
    @MethodSource("serializationDlqMappings")
    void shouldRouteSerializationExceptionDirectlyToCorrectDlq(
            final String sourceTopic,
            final int partition,
            final EventType expectedEventType,
            final String expectedDlqTopic
    ) {
        // Given
        switch (expectedEventType) {
            case ORDER -> {
                when(kafkaProperties.orderTopicName()).thenReturn("order-topic");
                when(kafkaDlqProperties.orderDlqTopicName()).thenReturn("order-dlq");
            }
            case PAYMENT -> {
                when(kafkaProperties.paymentTopicName()).thenReturn("payment-topic");
                when(kafkaDlqProperties.paymentDlqTopicName()).thenReturn("payment-dlq");
            }
        }
        final ConsumerRecord<String, Object> record =
                new ConsumerRecord<>(sourceTopic, partition, 0L, "key", "bad-payload");

        // when
        final TopicPartition destination = configuration.resolveDlqPartition(record);

        // then
        assertEquals(expectedDlqTopic, destination.topic());
        assertEquals(partition, destination.partition());
    }

    /**
     * Verifies that a source topic is correctly resolved to its corresponding
     * {@link EventType} and mapped to the appropriate DLQ topic and partition.
     */
    @ParameterizedTest(name = "topic={0} should resolve to DLQ={2}")
    @MethodSource("topicToDlqMappings")
    void shouldResolveTopicToCorrectDlq(
            final String sourceTopic,
            final int partitions,
            final EventType expectedEventType,
            final String expectedDlqTopic
    ) {
        // Given
        switch (expectedEventType) {
            case PAYMENT -> {
                when(kafkaProperties.paymentTopicName()).thenReturn("payment-topic");
                when(kafkaDlqProperties.paymentDlqTopicName()).thenReturn("payment-dlq");
            }
            case ORDER -> {
                when(kafkaProperties.orderTopicName()).thenReturn("order-topic");
                when(kafkaDlqProperties.orderDlqTopicName()).thenReturn("order-dlq");
            }
        }

        final EventType actualEventType =
                configuration.resolveEventType(sourceTopic);
        final ConsumerRecord<String, Object> record =
                new ConsumerRecord<>(sourceTopic, partitions, 0L, "key", "value");


        // When
        final TopicPartition topicPartition = configuration.resolveDlqPartition(record);

        // then
        assertEquals(expectedEventType, actualEventType);
        assertEquals(expectedDlqTopic, topicPartition.topic());
        assertEquals(partitions, topicPartition.partition());
    }

    /**
     * Verifies that an {@link IllegalArgumentException} is thrown when
     * an unknown Kafka topic is encountered.
     *
     * <p>
     * This ensures misconfigured or unexpected topics fail fast instead
     * of being silently routed to an incorrect DLQ.
     * </p>
     */
    @Test
    void shouldFailFastForUnknownTopic() {
        // given
        when(kafkaProperties.paymentTopicName()).thenReturn("payment-topic");
        when(kafkaProperties.orderTopicName()).thenReturn("order-topic");

        // when + then
        final IllegalArgumentException ex =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> configuration.resolveEventType("unknown-topic")
                );
        assertEquals(
                "No DLQ mapping configured for source topic: unknown-topic",
                ex.getMessage()
        );
    }

    /**
     * Verifies that the retry listener is invoked during a retry attempt
     * and logs the retry metadata correctly.
     *
     * <p>
     * This test ensures that retry behavior does not throw exceptions
     * and that retry attempts are observable via logs.
     * </p>
     */
    @ParameterizedTest(name = "topic={0} should invoke retry listener and log retry attempt")
    @MethodSource("topicMappings")
    void shouldInvokeRetryListenerAndLogRetryAttempt(final String topicName) {
        // given
        when(kafkaDlqProperties.maxAttempts()).thenReturn(2);
        when(kafkaDlqProperties.backOffInterval()).thenReturn(Math.toIntExact(100L));
        when(kafkaDlqProperties.multiplier()).thenReturn(2.0);
        when(kafkaDlqProperties.maxInterval()).thenReturn(Math.toIntExact(1_000L));

        final KafkaTemplate<String, Object> template = mock(KafkaTemplate.class);
        final DefaultErrorHandler handler = configuration.errorHandler(template);
        final LogCaptor logCaptor = LogCaptor.forClass(KafkaErrorHandlerConfigurations.class);
        final ConsumerRecord<String, Object> record =
                new ConsumerRecord<>(topicName, 0, 0L, "key", "value");
        final RuntimeException exception = new RuntimeException("boom");

        // when
        assertDoesNotThrow(() ->
                handler.handleOne(exception, record, null, null)
        );

        // then
        assertTrue(
                logCaptor.getWarnLogs().stream()
                        .anyMatch(log ->
                                log.contains("Retry #1") &&
                                        log.contains(topicName) &&
                                        log.contains("error=boom")
                        ),
                "Retry listener should log retry attempt"
        );
    }

    /**
     * Verifies that a {@link IllegalArgumentException} is thrown when
     * a null source topic is provided.
     *
     * <p>
     * This protects the system from undefined DLQ routing behavior.
     * </p>
     */
    @Test
    void shouldThrowExceptionWhenSourceTopicIsNull() {
        // when + then
        final IllegalArgumentException ex =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> configuration.resolveEventType(null)
                );

        assertEquals(
                "No DLQ mapping configured for source topic: null",
                ex.getMessage()
        );
    }

    /**
     * Provides topic names used to verify retry listener invocation.
     */
    private static Stream<Arguments> topicMappings() {
        return Stream.of(
                Arguments.of("payment-topic"),
                Arguments.of("order-topic")
        );
    }

    /**
     * Provides mappings between source topics and their expected DLQ targets.
     */
    private static Stream<Arguments> topicToDlqMappings() {
        return Stream.of(
                Arguments.of("payment-topic", 10, EventType.PAYMENT, "payment-dlq"),
                Arguments.of("order-topic", 10, EventType.ORDER, "order-dlq")
        );
    }

    /**
     * Provides mappings used to verify DLQ routing for serialization failures.
     */
    private static Stream<Arguments> serializationDlqMappings() {
        return Stream.of(
                Arguments.of("payment-topic", 0, EventType.PAYMENT, "payment-dlq"),
                Arguments.of("order-topic", 1, EventType.ORDER, "order-dlq")
        );
    }
}
