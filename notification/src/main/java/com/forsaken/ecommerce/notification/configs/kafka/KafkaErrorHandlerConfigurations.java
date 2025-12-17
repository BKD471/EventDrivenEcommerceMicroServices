package com.forsaken.ecommerce.notification.configs.kafka;

import com.forsaken.ecommerce.notification.models.EventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

import java.util.HashMap;
import java.util.Map;


@Configuration
@RequiredArgsConstructor
@Slf4j
public class KafkaErrorHandlerConfigurations {

    private final KafkaProperties kafkaProperties;
    private final KafkaDlqProperties kafkaDlqProperties;

    @Bean
    public KafkaTemplate<String, Object> avroKafkaTemplate() {
        final Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.bootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, io.confluent.kafka.serializers.KafkaAvroSerializer.class);
        props.put("schema.registry.url", kafkaProperties.schemaRegistryUrl());
        props.put("specific.avro.reader", true);
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
    }

    @Bean
    public DefaultErrorHandler errorHandler(final KafkaTemplate<String, Object> avroKafkaTemplate) {
        final DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                avroKafkaTemplate,
                (record, ex) -> {
                    // choose DLQ topic based on source topic (optional)
                    final String sourceTopic = record.topic();
                    final EventType eventType = resolveEventType(sourceTopic);
                    final String dlqTopic = switch (eventType) {
                        case PAYMENT -> kafkaDlqProperties.paymentDlqTopicName();
                        case ORDER -> kafkaDlqProperties.orderDlqTopicName();
                    };
                    return new TopicPartition(dlqTopic, record.partition());
                }
        );

        // Exponential backoff: maxAttempts = dlq.maxAttempts (3) => (attempts include initial? DefaultErrorHandler will do attempts)
        final ExponentialBackOffWithMaxRetries backOff =
                new ExponentialBackOffWithMaxRetries(kafkaDlqProperties.maxAttempts());
        backOff.setInitialInterval(kafkaDlqProperties.backOffInterval());
        backOff.setMultiplier(kafkaDlqProperties.multiplier());
        backOff.setMaxInterval(kafkaDlqProperties.maxInterval());

        final DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(SerializationException.class);
        handler.setRetryListeners((record, ex, deliveryAttempt) ->
                log.warn("Retry #{} for topic={} partition={} offset={} key={} error={}",
                        deliveryAttempt,
                        record.topic(),
                        record.partition(),
                        record.offset(),
                        record.key(),
                        ex.getMessage())
        );
        return handler;
    }

    private EventType resolveEventType(final String sourceTopic) {
        return switch (sourceTopic) {
            case null -> throw new IllegalArgumentException(
                    "No DLQ mapping configured for source topic: null"
            );
            case String topicName when topicName.equals(kafkaProperties.paymentTopicName()) -> EventType.PAYMENT;
            case String topicName when topicName.equals(kafkaProperties.orderTopicName()) -> EventType.ORDER;
            default -> throw new IllegalArgumentException(
                    "No DLQ mapping configured for source topic: " + sourceTopic
            );
        };
    }
}
