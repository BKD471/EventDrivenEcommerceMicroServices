package com.forsaken.ecommerce.order.configs.aurora;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AwsAuroraConfig}.
 *
 * <p>
 * This test class verifies the behavior of the {@code awsDbCredentials()} bean
 * method in isolation, without starting a Spring ApplicationContext.
 * </p>
 *
 * <p>
 * <b>Testing strategy:</b>
 * </p>
 * <ul>
 *   <li>Uses Mockito to mock {@link SecretsManagerClient} and
 *       {@link SecretsManagerProperties}.</li>
 *   <li>Uses a real {@link ObjectMapper} to validate actual JSON deserialization.</li>
 *   <li>Directly instantiates {@link AwsAuroraConfig} to keep tests fast and deterministic.</li>
 * </ul>
 *
 * <p>
 * These tests do <strong>not</strong> verify {@code @Bean} creation via Spring.
 * They focus strictly on the logic of loading and parsing database credentials
 * from AWS Secrets Manager.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class AwsAuroraConfigTest {

    @Mock
    private SecretsManagerProperties secretsManagerProperties;

    @Mock
    private SecretsManagerClient secretsManagerClient;

    private ObjectMapper objectMapper;

    private AwsAuroraConfig awsAuroraConfig;

    /**
     * Initializes the {@link AwsAuroraConfig} under test with mocked dependencies.
     *
     * <p>
     * A real {@link ObjectMapper} is used to ensure JSON parsing behaves exactly
     * as it would in production.
     * </p>
     */
    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        awsAuroraConfig =
                new AwsAuroraConfig(secretsManagerProperties, secretsManagerClient, objectMapper);
    }

    /**
     * Verifies that database credentials are successfully loaded and mapped
     * when AWS Secrets Manager returns a valid JSON secret.
     *
     * <p>
     * This test ensures:
     * </p>
     * <ul>
     *   <li>The secret name is resolved from {@link SecretsManagerProperties}.</li>
     *   <li>The secret JSON is correctly deserialized.</li>
     *   <li>All expected credential fields are populated correctly.</li>
     *   <li>The AWS Secrets Manager client is invoked exactly once.</li>
     * </ul>
     */
    @Test
    void shouldLoadAwsDbCredentialsSuccessfully() throws Exception {
        // given
        when(secretsManagerProperties.dbSecretName())
                .thenReturn("aurora/db/credentials");
        final String secretJson = """
                {
                  "postgress_username": "db_user",
                  "postgress_password": "db_password",
                  "postgress_host": "aurora.cluster.amazonaws.com",
                  "port": "5432",
                  "dbname": "order_db"
                }
                """;
        final GetSecretValueResponse response = GetSecretValueResponse.builder()
                .secretString(secretJson)
                .build();
        when(secretsManagerClient.getSecretValue(any(GetSecretValueRequest.class)))
                .thenReturn(response);

        // when
        final AwsDbCredentials credentials = awsAuroraConfig.awsDbCredentials();

        // then
        assertThat(credentials).isNotNull();
        assertThat(credentials.userName()).isEqualTo("db_user");
        assertThat(credentials.password()).isEqualTo("db_password");
        assertThat(credentials.host()).isEqualTo("aurora.cluster.amazonaws.com");
        assertThat(credentials.port()).isEqualTo("5432");
        assertThat(credentials.dbName()).isEqualTo("order_db");
        verify(secretsManagerClient, times(1))
                .getSecretValue(any(GetSecretValueRequest.class));
    }

    /**
     * Verifies that a runtime exception is thrown when AWS Secrets Manager
     * fails to return a secret.
     *
     * <p>
     * This test simulates an AWS-side failure (for example, network issues
     * or permission errors) and asserts that:
     * </p>
     * <ul>
     *   <li>The exception is wrapped in a {@link RuntimeException}.</li>
     *   <li>A meaningful error message is propagated.</li>
     *   <li>The original exception is preserved as the root cause.</li>
     * </ul>
     */
    @Test
    void shouldThrowExceptionWhenSecretsManagerFails() {
        // given
        when(secretsManagerProperties.dbSecretName())
                .thenReturn("aurora/db/credentials");
        when(secretsManagerClient.getSecretValue(any(GetSecretValueRequest.class)))
                .thenThrow(new RuntimeException("AWS down"));

        // when / then
        assertThatThrownBy(() -> awsAuroraConfig.awsDbCredentials())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to load AWS credentials from Secrets Manager")
                .hasRootCauseMessage("AWS down");
    }

    /**
     * Verifies that a runtime exception is thrown when the secret value
     * contains invalid JSON.
     *
     * <p>
     * This test ensures that malformed or corrupted secrets do not fail
     * silently and are reported as configuration errors during application
     * startup.
     * </p>
     */
    @Test
    void shouldThrowExceptionWhenSecretJsonIsInvalid() throws Exception {
        // given
        when(secretsManagerProperties.dbSecretName())
                .thenReturn("aurora/db/credentials");
        final String invalidJson = "{ this-is-not-valid-json }";
        final GetSecretValueResponse response = GetSecretValueResponse.builder()
                .secretString(invalidJson)
                .build();
        when(secretsManagerClient.getSecretValue(any(GetSecretValueRequest.class)))
                .thenReturn(response);

        // when / then
        assertThatThrownBy(() -> awsAuroraConfig.awsDbCredentials())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to load AWS credentials from Secrets Manager");
    }
}