package com.forsaken.ecommerce.notification.configs.mail;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for {@link MailConfigurations}.
 *
 * <p>
 * This test suite verifies the fail-fast behavior and correctness of
 * {@link MailConfigurations#javaMailSender()} without loading a Spring
 * application context.
 * </p>
 *
 * <h2>Test scope</h2>
 * <ul>
 *   <li>Pure unit testing (no Spring Test, no ApplicationContext).</li>
 *   <li>Validation of required SMTP configuration properties.</li>
 *   <li>Correct construction and configuration of {@link JavaMailSenderImpl}.</li>
 * </ul>
 *
 * <h2>Covered scenarios</h2>
 * <ul>
 *   <li>Successful creation of {@link JavaMailSenderImpl} when all required
 *       mail properties are present.</li>
 *   <li>Fail-fast behavior when mail properties are {@code null} or empty.</li>
 *   <li>Fail-fast behavior when any required SMTP property is missing
 *       (validated via parameterized tests).</li>
 * </ul>
 *
 * <p>
 * The tests assert both behavior and error messages to ensure configuration
 * failures are explicit, deterministic, and actionable.
 * </p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MailConfigurationsTest {

    private static final String TEST_HOST = "localhost";
    private static final int TEST_PORT = 1025;
    private static final String TEST_USERNAME = "admin";
    private static final String TEST_PASSWORD = "admin";

    /**
     * Mandatory SMTP properties for this application.
     *
     * <p>
     * These properties are intentionally enforced to guarantee:
     * </p>
     * <ul>
     *   <li>Authenticated SMTP communication</li>
     *   <li>Explicit TLS configuration</li>
     *   <li>Deterministic timeout behavior</li>
     * </ul>
     *
     * <p>
     * This application does not support implicit defaults or partially
     * configured SMTP servers. All notification delivery must be explicit,
     * secure, and fail-fast.
     * </p>
     */
    private static final String[] REQUIRED_SMTP_KEYS_TEST = {
            "mail.smtp.auth",
            "mail.smtp.starttls.enable",
            "mail.smtp.connectiontimeout",
            "mail.smtp.timeout",
            "mail.smtp.writetimeout"
    };

    /**
     * Verifies that a {@link JavaMailSenderImpl} is successfully created
     * when all required SMTP properties are present.
     *
     * <p>
     * This test asserts:
     * </p>
     * <ul>
     *   <li>SMTP connection parameters (host, port, username, password) are applied.</li>
     *   <li>All required JavaMail session properties are copied correctly.</li>
     * </ul>
     *
     * <p>
     * This represents the <strong>happy path</strong> and intentionally avoids
     * loops or parameterization to keep the assertions explicit and readable.
     * </p>
     */
    @Test
    @DisplayName("Should create JavaMailSender when all required SMTP properties are present")
    void shouldCreateJavaMailSenderSuccessfully() {
        // Given
        final MailConfigurations configuration =
                constructConfigurationWith(constructValidMailProperties());

        // When
        final JavaMailSenderImpl sender =
                (JavaMailSenderImpl) configuration.javaMailSender();

        // Then
        assertNotNull(sender);
        assertEquals(TEST_HOST, sender.getHost());
        assertEquals(TEST_PORT, sender.getPort());
        assertEquals(TEST_USERNAME, sender.getUsername());
        assertEquals(TEST_PASSWORD, sender.getPassword());

        final Properties javaMailProperties = sender.getJavaMailProperties();
        assertNotNull(javaMailProperties);
        assertEquals("true", javaMailProperties.getProperty("mail.smtp.auth"));
        assertEquals("true", javaMailProperties.getProperty("mail.smtp.starttls.enable"));
        assertEquals("5000", javaMailProperties.getProperty("mail.smtp.connectiontimeout"));
        assertEquals("3000", javaMailProperties.getProperty("mail.smtp.timeout"));
        assertEquals("5000", javaMailProperties.getProperty("mail.smtp.writetimeout"));
    }

    /**
     * Verifies fail-fast behavior when the mail properties map is {@code null}.
     *
     * <p>
     * The configuration must reject {@code null} mail properties to prevent
     * partially configured {@link JavaMailSenderImpl} instances from being created.
     * </p>
     *
     * <p>
     * The test asserts that an {@link IllegalStateException} is thrown with
     * a clear and actionable error message.
     * </p>
     */
    @ParameterizedTest
    @NullSource
    @DisplayName("Should fail fast when mail properties map is null")
    void shouldFailWhenPropertiesAreNull(final Map<String, String> props) {
        // Given
        final MailConfigurations configuration = constructConfigurationWith(props);

        // When / Then
        final IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                configuration::javaMailSender
        );
        assertEquals(
                "Mail properties must not be empty. Required SMTP properties: " +
                        String.join(", ", REQUIRED_SMTP_KEYS_TEST),
                ex.getMessage()
        );
    }

    /**
     * Verifies fail-fast behavior when the mail properties map is empty.
     *
     * <p>
     * An empty properties map indicates missing SMTP protocol configuration
     * (such as authentication or TLS settings) and must be rejected at startup.
     * </p>
     */
    @Test
    @DisplayName("Should fail fast when mail properties map is empty")
    void shouldFailWhenPropertiesAreEmpty() {
        // Given
        final MailConfigurations configuration = constructConfigurationWith(Map.of());

        // When / Then
        final IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                configuration::javaMailSender
        );
        assertEquals(
                "Mail properties must not be empty. Required SMTP properties: " +
                        String.join(", ", REQUIRED_SMTP_KEYS_TEST),
                ex.getMessage()
        );
    }

    /**
     * Verifies fail-fast behavior when any required SMTP property is missing.
     *
     * <p>
     * This parameterized test removes exactly one required SMTP property
     * at a time and asserts that application startup fails immediately.
     * </p>
     *
     * <p>
     * Each execution validates:
     * </p>
     * <ul>
     *   <li>The correct {@link IllegalStateException} is thrown.</li>
     *   <li>The error message explicitly identifies the missing property.</li>
     * </ul>
     *
     * @param missingKey the required SMTP property key that is intentionally removed
     */
    @ParameterizedTest(name = "Missing required SMTP property: {0}")
    @MethodSource("missingRequiredPropertyProvider")
    @DisplayName("Should fail fast when a required SMTP property is missing")
    void shouldFailWhenRequiredPropertyIsMissing(final String missingKey) {
        // Given
        final Map<String, String> incompleteProps = new HashMap<>(constructValidMailProperties());
        incompleteProps.remove(missingKey);
        final MailConfigurations configuration = constructConfigurationWith(incompleteProps);

        // When / Then
        final IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                configuration::javaMailSender
        );
        assertEquals(
                "Missing required mail property: " + missingKey,
                ex.getMessage()
        );
    }

    /**
     * Verifies fail-fast behavior when SMTP timeout configuration values are invalid.
     * <p>
     * This parameterized test ensures that the mail configuration rejects
     * malformed or invalid timeout values <strong>before</strong> a
     * {@link org.springframework.mail.javamail.JavaMailSender} is created.
     * </p>
     *
     * <p>
     * Timeout-related SMTP properties are expected to be positive integers
     * expressed as strings, as required by the underlying JavaMail implementation.
     * Invalid values would otherwise cause obscure runtime failures during
     * mail delivery.
     * </p>
     *
     * <h3>Validation scenarios covered</h3>
     * <ul>
     *   <li>Negative timeout values (e.g. {@code -1})</li>
     *   <li>Zero timeout values (e.g. {@code 0})</li>
     *   <li>Non-numeric timeout values (e.g. {@code abc})</li>
     * </ul>
     *
     * <p>
     * For each invalid input, the test asserts that application startup
     * fails immediately with an {@link IllegalStateException}, enforcing
     * strict configuration correctness.
     * </p>
     *
     * @param key   the SMTP property key being validated
     * @param value the invalid timeout value to test
     */
    @ParameterizedTest
    @MethodSource("invalidTimeoutProvider")
    @DisplayName("Should fail fast when timeout values are invalid")
    void shouldFailWhenTimeoutIsInvalid(final String key, final String value) {
        // given
        final Map<String, String> props = new HashMap<>(constructValidMailProperties());
        props.put(key, value);

        // when / then
        final MailConfigurations configuration = constructConfigurationWith(props);
        assertThrows(IllegalStateException.class, configuration::javaMailSender);
    }

    /**
     * Verifies fail-fast validation for boolean-based SMTP configuration properties.
     *
     * <p>
     * This test ensures that mail configuration fails immediately when a boolean
     * SMTP property (such as {@code mail.smtp.auth} or
     * {@code mail.smtp.starttls.enable}) is provided with an invalid value.
     * </p>
     *
     * <p>
     * Only the literal values {@code "true"} and {@code "false"} (case-insensitive)
     * are considered valid. Any other value—including numeric strings, empty values,
     * or arbitrary text—must result in an {@link IllegalStateException}.
     * </p>
     *
     * <h3>Why this matters</h3>
     * <ul>
     *   <li>JavaMail silently accepts invalid boolean values and misinterprets them at runtime.</li>
     *   <li>Fail-fast validation prevents obscure SMTP connection failures.</li>
     *   <li>Ensures configuration errors are detected during application startup.</li>
     * </ul>
     *
     * <h3>Test strategy</h3>
     * <ul>
     *   <li>Uses {@link ParameterizedTest} to validate multiple invalid values.</li>
     *   <li>Mutates a known-good configuration to isolate the failing property.</li>
     *   <li>Asserts both exception type and error message for clarity.</li>
     * </ul>
     *
     * @param key   the SMTP property key under validation
     * @param value the invalid boolean value supplied for the property
     */
    @ParameterizedTest(name = "Invalid boolean value for {0} = {1}")
    @MethodSource("invalidBooleanProvider")
    @DisplayName("Should fail fast when boolean SMTP properties are invalid")
    void shouldFailWhenBooleanPropertyIsInvalid(String key, String value) {
        // given
        final Map<String, String> props = new HashMap<>(constructValidMailProperties());
        props.put(key, value);
        final MailConfigurations configuration = constructConfigurationWith(props);

        // when / then
        final IllegalStateException ex =
                assertThrows(IllegalStateException.class, configuration::javaMailSender);
        assertEquals(
                "Invalid boolean value for mail property '" + key + "': " + value,
                ex.getMessage()
        );
    }

    /**
     * Supplies invalid SMTP timeout configurations for parameterized testing.
     * <p>
     * Each argument pair represents a timeout-related SMTP property key
     * and an invalid value that should be rejected by the mail configuration.
     * </p>
     *
     * <p>
     * These cases intentionally violate expected constraints:
     * </p>
     * <ul>
     *   <li>Negative numeric values</li>
     *   <li>Zero values (timeouts must be positive)</li>
     *   <li>Non-numeric strings</li>
     * </ul>
     *
     * <p>
     * The provider is designed to ensure comprehensive coverage of
     * common misconfiguration scenarios that may otherwise only surface
     * at runtime.
     * </p>
     *
     * @return a stream of invalid SMTP timeout configurations
     */
    private static Stream<Arguments> invalidTimeoutProvider() {
        return Stream.of(
                Arguments.of("mail.smtp.timeout", "-1"),
                Arguments.of("mail.smtp.timeout", "0"),
                Arguments.of("mail.smtp.timeout", "abc")
        );
    }

    /**
     * Constructs a valid set of SMTP mail properties containing all
     * required configuration keys.
     *
     * @return a fully populated mail properties map
     */
    private Map<String, String> constructValidMailProperties() {
        return Map.of(
                "mail.smtp.auth", "true",
                "mail.smtp.starttls.enable", "true",
                "mail.smtp.connectiontimeout", "5000",
                "mail.smtp.timeout", "3000",
                "mail.smtp.writetimeout", "5000"
        );
    }

    /**
     * Creates a {@link MailConfigurations} instance using the provided
     * mail properties.
     *
     * <p>
     * This helper avoids Spring context initialization and allows
     * precise control over configuration inputs.
     * </p>
     *
     * @param props mail properties to apply
     * @return a configured {@link MailConfigurations} instance
     */
    private MailConfigurations constructConfigurationWith(final Map<String, String> props) {
        return new MailConfigurations(
                new MailProperties(
                        TEST_HOST,
                        TEST_PORT,
                        TEST_USERNAME,
                        TEST_PASSWORD,
                        props
                )
        );
    }

    /**
     * Supplies the list of required SMTP property keys used for
     * parameterized validation tests.
     *
     * @return stream of required SMTP property keys
     */
    private Stream<String> missingRequiredPropertyProvider() {
        return Stream.of(
                "mail.smtp.auth",
                "mail.smtp.starttls.enable",
                "mail.smtp.connectiontimeout",
                "mail.smtp.timeout",
                "mail.smtp.writetimeout"
        );
    }

    /**
     * Supplies invalid boolean values for SMTP-related mail properties.
     *
     * <p>
     * This provider intentionally includes values that are commonly misused or
     * accidentally configured in YAML or environment variables.
     * </p>
     *
     * <h3>Covered invalid cases</h3>
     * <ul>
     *   <li>Non-boolean text values (e.g. {@code "yes"}, {@code "enabled"})</li>
     *   <li>Numeric representations (e.g. {@code "1"})</li>
     *   <li>Empty strings</li>
     *   <li>String literals that look like null values</li>
     * </ul>
     *
     * <p>
     * These cases ensure the configuration layer strictly enforces valid boolean
     * semantics rather than relying on JavaMail’s permissive parsing.
     * </p>
     *
     * @return a stream of invalid SMTP boolean property key/value pairs
     */
    private Stream<Arguments> invalidBooleanProvider() {
        return Stream.of(
                Arguments.of("mail.smtp.auth", "yes"),
                Arguments.of("mail.smtp.auth", "1"),
                Arguments.of("mail.smtp.auth", ""),
                Arguments.of("mail.smtp.starttls.enable", "enabled"),
                Arguments.of("mail.smtp.starttls.enable", "null")
        );
    }
}
