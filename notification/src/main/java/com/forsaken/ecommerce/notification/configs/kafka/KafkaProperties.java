package com.forsaken.ecommerce.notification.configs.kafka;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;


@Validated
@ConfigurationProperties(prefix = "spring.kafka.consumer")
public record KafkaProperties(
        @NotBlank
        String paymentGroupId,

        @NotBlank
        String orderGroupId,

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
}
