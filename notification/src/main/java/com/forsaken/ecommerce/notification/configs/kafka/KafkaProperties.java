package com.forsaken.ecommerce.notification.configs.kafka;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;


@Validated
@ConfigurationProperties(prefix = "spring.kafka.consumer")
public record KafkaProperties(
        @NotBlank
        String paymentTopicName,

        @NotBlank
        String paymentGroupId,

        @NotBlank
        String orderTopicName,

        @NotBlank
        String orderGroupId,

        @NotNull
        Duration maxPollIntervalMs,

        @Min(value = 1, message = "max-poll-records must be >= 1")
        int maxPollRecords,

        @NotBlank
        String offSetReset,

        @NotBlank
        String schemaRegistryUrl,

        @NotBlank
        String timeZone,

        @NotEmpty
        List<String> bootstrapServers,

        @NotNull
        Class<?> keyDeSerializer,

        @NotNull
        Class<?> valueDeSerializer
) {

    public KafkaProperties {
        if (maxPollIntervalMs.isZero() || maxPollIntervalMs.isNegative()) {
            throw new IllegalArgumentException(
                    "spring.kafka.consumer.max-poll-interval must be positive"
            );
        }
    }
}
