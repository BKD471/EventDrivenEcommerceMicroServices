package com.forsaken.ecommerce.order.configs.aurora;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "aws.secretsmanager")
public record SecretsManagerProperties(
        @NotBlank
        String dbSecretName,

        @NotBlank
        String region
) {
}
