package com.forsaken.ecommerce.notification.configs.kafka;

import com.forsaken.ecommerce.avro.OrderConfirmation;
import com.forsaken.ecommerce.avro.PaymentConfirmation;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.RecordInterceptor;

import java.util.AbstractMap;
import java.util.Map;

import static io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG;
import static org.apache.kafka.clients.consumer.ConsumerConfig.AUTO_OFFSET_RESET_CONFIG;
import static org.apache.kafka.clients.consumer.ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG;
import static org.apache.kafka.clients.consumer.ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG;
import static org.apache.kafka.clients.consumer.ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG;
import static org.apache.kafka.clients.consumer.ConsumerConfig.MAX_POLL_RECORDS_CONFIG;
import static org.apache.kafka.clients.consumer.ConsumerConfig.RETRY_BACKOFF_MS_CONFIG;
import static org.apache.kafka.clients.consumer.ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG;


@Configuration
@RequiredArgsConstructor
@Slf4j
public class KafkaConsumerConfigurations {

    private final KafkaProperties kafkaProperties;
    private final KafkaDlqProperties kafkaDlqProperties;

    @Bean
    public NewTopic paymentNotificationsDlqTopic() {
        return TopicBuilder
                .name(kafkaDlqProperties.paymentDlqTopicName())
                .partitions(kafkaDlqProperties.partitions())
                .replicas(kafkaDlqProperties.replicas())
                .build();
    }


    @Bean
    public NewTopic orderNotificationsDlqTopic() {
        return TopicBuilder
                .name(kafkaDlqProperties.orderDlqTopicName())
                .partitions(kafkaDlqProperties.partitions())
                .replicas(kafkaDlqProperties.replicas())
                .build();
    }


    @Bean
    public ConsumerFactory<String, Object> orderConsumerFactory() {
        final Map<String, Object> props = constructConsumerFactory(kafkaProperties.orderGroupId());
        return new DefaultKafkaConsumerFactory<>(
                props,
                new StringDeserializer(),
                new KafkaAvroDeserializer() {{
                    configure(props, false);
                }}
        );
    }


    @Bean(name = "orderKafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, OrderConfirmation> orderKafkaListenerContainerFactory(
            final DefaultErrorHandler errorHandler,
            final RecordInterceptor<String, Object> kafkaTracingInterceptor
    ) {
        final ConcurrentKafkaListenerContainerFactory<String, OrderConfirmation> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(orderConsumerFactory());
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        factory.setBatchListener(false);
        factory.setCommonErrorHandler(errorHandler);
        factory.setRecordInterceptor((RecordInterceptor<String, OrderConfirmation>)
                (RecordInterceptor) kafkaTracingInterceptor);
        return factory;
    }


    @Bean
    public ConsumerFactory<String, Object> paymentConsumerFactory() {
        final Map<String, Object> props = constructConsumerFactory(kafkaProperties.paymentGroupId());
        return new DefaultKafkaConsumerFactory<>(
                props,
                new StringDeserializer(),
                new KafkaAvroDeserializer() {{
                    configure(props, false);
                }}
        );
    }


    @Bean(name = "paymentKafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, PaymentConfirmation> paymentKafkaListenerContainerFactory(
            final DefaultErrorHandler errorHandler,
            final RecordInterceptor<String, Object> kafkaTracingInterceptor
    ) {
        final ConcurrentKafkaListenerContainerFactory<String, PaymentConfirmation> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(paymentConsumerFactory());
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        factory.setBatchListener(false);
        factory.setCommonErrorHandler(errorHandler);
        factory.setRecordInterceptor((RecordInterceptor<String, PaymentConfirmation>)
                (RecordInterceptor) kafkaTracingInterceptor);
        return factory;
    }


    private Map<String, Object> constructConsumerFactory(final String groupId) {
        return Map.ofEntries(
                new AbstractMap.SimpleEntry<>(
                        BOOTSTRAP_SERVERS_CONFIG,
                        kafkaProperties.bootstrapServers()
                ),
                new AbstractMap.SimpleEntry<>(
                        ConsumerConfig.GROUP_ID_CONFIG,
                        groupId
                ),
                new AbstractMap.SimpleEntry<>(
                        SCHEMA_REGISTRY_URL_CONFIG,
                        kafkaProperties.schemaRegistryUrl()
                ),
                new AbstractMap.SimpleEntry<>(
                        MAX_POLL_INTERVAL_MS_CONFIG,
                        Math.toIntExact(kafkaProperties.maxPollIntervalMs().toMillis())
                ),
                new AbstractMap.SimpleEntry<>(
                        MAX_POLL_RECORDS_CONFIG,
                        kafkaProperties.maxPollRecords()
                ),
                new AbstractMap.SimpleEntry<>(
                        "specific.avro.reader",
                        true
                ),
                new AbstractMap.SimpleEntry<>(
                        AUTO_OFFSET_RESET_CONFIG,
                        kafkaProperties.offSetReset()
                ),
                new AbstractMap.SimpleEntry<>(
                        KEY_DESERIALIZER_CLASS_CONFIG,
                        kafkaProperties.keyDeSerializer()
                ),
                new AbstractMap.SimpleEntry<>(
                        VALUE_DESERIALIZER_CLASS_CONFIG,
                        kafkaProperties.valueDeSerializer()
                )
        );
    }
}
