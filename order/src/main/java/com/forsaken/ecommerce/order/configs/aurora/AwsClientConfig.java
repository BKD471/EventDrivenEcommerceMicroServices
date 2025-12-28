package com.forsaken.ecommerce.order.configs.aurora;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

@Configuration
@RequiredArgsConstructor
public class AwsClientConfig {

    private final SecretsManagerProperties secretsManagerProperties;

    @Bean
    public SecretsManagerClient secretsManagerClient() {
        return SecretsManagerClient.builder()
                .region(Region.of(secretsManagerProperties.region()))
                .build();
    }
}
