package com.forsaken.ecommerce.notification.configs.dynamodb;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

@Configuration
@RequiredArgsConstructor
public class DynamoDBConfigurations {

    private final AwsCredentialsProperties awsCredentials;

    @Bean
    @Primary
    public DynamoDbClient dynamoDbClient() {
        return DynamoDbClient.builder()
                .region(Region.of(awsCredentials.region()))
                .credentialsProvider(credentialsProvider())
                .build();
    }

    @Bean
    @Primary
    public DynamoDbEnhancedClient dynamoDbEnhancedClient() {
        return DynamoDbEnhancedClient.builder()
                .dynamoDbClient(dynamoDbClient())
                .build();
    }

    private AwsCredentialsProvider credentialsProvider() {
        if (awsCredentials.accessKeyId() != null && !awsCredentials.accessKeyId().isBlank() &&
                awsCredentials.secretKey() != null && !awsCredentials.secretKey().isBlank()) {
            return StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(
                            awsCredentials.accessKeyId(),
                            awsCredentials.secretKey())
            );
        }
        return DefaultCredentialsProvider.create();
    }
}