package com.forsaken.ecommerce.notification.configs.s3;

import com.forsaken.ecommerce.notification.configs.dynamodb.AwsCredentialsProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@RequiredArgsConstructor
public class S3Configurations {

    private final AwsCredentialsProperties awsCredentials;


    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .region(Region.of(awsCredentials.region()))
                .credentialsProvider(credentialsProvider())
                .build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        return S3Presigner.builder()
                .region(Region.of(awsCredentials.region()))
                .credentialsProvider(credentialsProvider())
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