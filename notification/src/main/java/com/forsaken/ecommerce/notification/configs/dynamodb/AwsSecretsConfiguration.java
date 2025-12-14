package com.forsaken.ecommerce.notification.configs.dynamodb;

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
public class AwsSecretsConfiguration {

    private final SecretsManagerProperties secretsManagerProperties;

    @Bean
    public AwsCredentialsProperties awsCredentialsProperties() {
        final String secretName = secretsManagerProperties.secretName();
        final Region region = Region.AP_SOUTH_1;
        final SecretsManagerClient client = SecretsManagerClient.builder()
                .region(region)
                .build();
        final GetSecretValueRequest getSecretValueRequest = GetSecretValueRequest.builder()
                .secretId(secretName)
                .build();
        final GetSecretValueResponse getSecretValueResponse =
                client.getSecretValue(getSecretValueRequest);

        try {
            final ObjectMapper mapper = new ObjectMapper();
            final Map<String, String> secrets =
                    mapper.readValue(getSecretValueResponse.secretString(), Map.class);
            final AwsCredentialsProperties awsCredentialsProperties = AwsCredentialsProperties.builder()
                    .accessKeyId(secrets.get("awsAccessKey"))
                    .secretKey(secrets.get("awsSecretKey"))
                    .region(secrets.get("region"))
                    .build();
            return awsCredentialsProperties;
        } catch (Exception e) {
            throw new RuntimeException("Failed to load AWS credentials from Secrets Manager", e);
        }
    }
}
