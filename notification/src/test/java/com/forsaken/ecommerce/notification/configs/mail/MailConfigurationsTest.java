package com.forsaken.ecommerce.notification.configs.mail;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
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
class MailConfigurationsTest {

    private static final String TEST_HOST = "localhost";
    private static final int TEST_PORT = 1025;
    private static final String TEST_USERNAME = "admin";
    private static final String TEST_PASSWORD = "admin";

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
                "Mail properties must not be empty. Please configure required SMTP settings (e.g. authentication and TLS).",
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
                "Mail properties must not be empty. Please configure required SMTP settings (e.g. authentication and TLS).",
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
    private static MailConfigurations constructConfigurationWith(final Map<String, String> props) {
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
    private static Stream<String> missingRequiredPropertyProvider() {
        return Stream.of(
                "mail.smtp.auth",
                "mail.smtp.starttls.enable",
                "mail.smtp.connectiontimeout",
                "mail.smtp.timeout",
                "mail.smtp.writetimeout"
        );
    }
}
