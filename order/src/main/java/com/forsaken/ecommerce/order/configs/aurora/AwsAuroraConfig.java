package com.forsaken.ecommerce.order.configs.aurora;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

import java.util.Map;

@Configuration
@RequiredArgsConstructor
public class AwsAuroraConfig {

    private final SecretsManagerProperties secretsManagerProperties;
    private final SecretsManagerClient secretsManagerClient;
    private final ObjectMapper objectMapper;

    @Bean
    public AwsDbCredentials awsDbCredentials() {
        final String secretName = secretsManagerProperties.dbSecretName();
        final Region region = Region.AP_SOUTH_1;

        try {
            final GetSecretValueResponse getSecretValueResponse =
                    secretsManagerClient.getSecretValue(GetSecretValueRequest.builder()
                            .secretId(secretName)
                            .build());
            final Map<String, String> secrets =
                    objectMapper.readValue(getSecretValueResponse.secretString(), Map.class);
            return AwsDbCredentials.builder()
                    .userName(secrets.get("postgress_username"))
                    .password(secrets.get("postgress_password"))
                    .host(secrets.get("postgress_host"))
                    .port(secrets.get("port"))
                    .dbName(secrets.get("dbname"))
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to load AWS credentials from Secrets Manager", e);
        }
    }

    @Bean
    public SecretsManagerClient secretsManagerClient() {
        return SecretsManagerClient.builder()
                .region(Region.AP_SOUTH_1)
                .build();
    }
}
