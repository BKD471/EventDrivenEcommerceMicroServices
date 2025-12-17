package com.forsaken.ecommerce.notification.configs.kafka;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;


@Validated
@ConfigurationProperties(prefix = "spring.kafka.dlq")
public record KafkaDlqProperties(
        @NotBlank
        String paymentDlqTopicName,

        @NotBlank
        String orderDlqTopicName,

        @NotBlank
        String groupId,

        @Min(1)
        @Max(100)
        Integer maxAttempts,

        @Min(1)
        @Max(100)
        Integer partitions,

        @Min(1)
        @Max(100)
        Integer replicas,

        @Min(1)
        @Max(100000)
        Integer backOffInterval,

        @NotNull
        Double multiplier,

        @Min(1)
        @Max(100000)
        Integer maxInterval,

        @Min(1)
        @Max(10)
        Integer concurrency,

        @NotBlank
        String ack,

        @NotNull
        Boolean missingTopicsFatal
) {
}
