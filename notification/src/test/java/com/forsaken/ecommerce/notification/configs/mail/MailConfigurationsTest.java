package com.forsaken.ecommerce.notification.configs.mail;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;


import java.util.HashMap;
import java.util.List;
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
 * This test suite validates the fail-fast configuration behavior of the
 * JavaMail sender setup without relying on the Spring application context.
 * </p>
 *
 * <p>
 * The goal of these tests is to ensure that invalid SMTP configurations
 * are rejected early during application startup, producing deterministic
 * and actionable error messages instead of deferred runtime failures.
 * </p>
 *
 * <h2>Design principles</h2>
 * <ul>
 *   <li>Pure unit tests (no {@code @SpringBootTest}, no container startup).</li>
 *   <li>Explicit validation of error messages to prevent silent regressions.</li>
 *   <li>Fail-fast semantics for all critical SMTP properties.</li>
 *   <li>Readable test intent over compactness.</li>
 * </ul>
 *
 * <h2>What is intentionally NOT tested</h2>
 * <ul>
 *   <li>Actual SMTP connectivity.</li>
 *   <li>JavaMail internal behavior.</li>
 *   <li>Spring auto-configuration.</li>
 * </ul>
 *
 * <p>
 * These concerns are outside the responsibility of {@link MailConfigurations}
 * and are therefore excluded by design.
 * </p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
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
        final Map<String, String> incompleteProps = constructValidMailProperties();
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

    /**
     * Verifies fail-fast behavior when required boolean SMTP properties are
     * {@code null}, empty, or contain only whitespace.
     *
     * <p>
     * Boolean SMTP properties such as {@code mail.smtp.auth} and
     * {@code mail.smtp.starttls.enable} are required to have explicit boolean
     * values ({@code true} or {@code false}). A blank or missing value represents
     * a configuration error that must be detected during application startup.
     * </p>
     *
     * <p>
     * This test ensures that:
     * </p>
     * <ul>
     *   <li>Blank values (e.g. {@code ""} or whitespace-only strings) are rejected.</li>
     *   <li>The application fails fast with a clear, deterministic error message.</li>
     *   <li>Misconfigurations do not silently fall back to default behavior.</li>
     * </ul>
     *
     * <p>
     * This validation is intentionally separated from tests that handle
     * <em>non-boolean</em> values (such as {@code "yes"} or {@code "1"}) to
     * preserve semantic clarity between:
     * </p>
     * <ul>
     *   <li><strong>Missing values</strong> (null / blank)</li>
     *   <li><strong>Invalid values</strong> (not {@code true}/{@code false})</li>
     * </ul>
     *
     * @param key   the SMTP property key being validated
     * @param value the blank or whitespace-only value supplied for the property
     */
    @ParameterizedTest(name = "Blank boolean value for {0}")
    @MethodSource("blankBooleanProvider")
    @DisplayName("Should fail when boolean SMTP properties are blank or null")
    void shouldFailWhenBooleanPropertyIsBlank(String key, String value) {
        // given
        final Map<String, String> props = constructValidMailProperties();
        props.put(key, value);

        final MailConfigurations configuration = constructConfigurationWith(props);

        // when / then
        final IllegalStateException ex =
                assertThrows(IllegalStateException.class, configuration::javaMailSender);
        assertEquals(
                "Mail property '" + key + "' must not be null and must be either 'true' or 'false'",
                ex.getMessage()
        );
    }

    /**
     * Supplies blank or whitespace-only values for boolean SMTP properties.
     *
     * <p>
     * These inputs simulate common configuration mistakes such as:
     * </p>
     * <ul>
     *   <li>Leaving a property value empty in YAML or properties files</li>
     *   <li>Providing whitespace instead of a concrete boolean value</li>
     * </ul>
     *
     * <p>
     * Such configurations are invalid and must be rejected during application
     * startup to avoid ambiguous or environment-dependent behavior.
     * </p>
     *
     * @return a stream of SMTP property keys paired with blank values
     */
    private static Stream<Arguments> blankBooleanProvider() {
        return Stream.of(
                Arguments.of("mail.smtp.auth", ""),
                Arguments.of("mail.smtp.starttls.enable", "   ")
        );
    }

    /**
     * Verifies fail-fast behavior when required boolean SMTP properties
     * contain non-boolean values.
     *
     * <p>
     * Boolean SMTP properties (such as {@code mail.smtp.auth} and
     * {@code mail.smtp.starttls.enable}) must explicitly be set to
     * {@code true} or {@code false}. Any other value is considered
     * invalid and must be rejected during application startup.
     * </p>
     *
     * <p>
     * This test ensures that:
     * </p>
     * <ul>
     *   <li>Non-boolean values (e.g. {@code "yes"}, {@code "1"}, {@code "enabled"}) are rejected.</li>
     *   <li>The configuration fails fast before creating a {@link org.springframework.mail.javamail.JavaMailSender}.</li>
     *   <li>The error message clearly identifies the offending property and value.</li>
     * </ul>
     *
     * <p>
     * This validation is intentionally separate from tests that handle
     * <em>blank</em> or <em>missing</em> values to preserve semantic clarity
     * between missing and invalid configurations.
     * </p>
     *
     * @param key   the boolean SMTP property key being validated
     * @param value the invalid (non-boolean) value supplied
     */
    @ParameterizedTest(name = "Invalid boolean value for {0} = {1}")
    @MethodSource("invalidBooleanProvider")
    @DisplayName("Should fail when boolean SMTP properties are not true or false")
    void shouldFailWhenBooleanPropertyIsInvalid(String key, String value) {
        // given
        final Map<String, String> props = constructValidMailProperties();
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
     * Supplies invalid non-boolean values for boolean SMTP properties.
     *
     * <p>
     * These values intentionally violate the expected boolean contract
     * and simulate common configuration mistakes such as:
     * </p>
     * <ul>
     *   <li>Using human-readable values (e.g. {@code "yes"}, {@code "enabled"})</li>
     *   <li>Using numeric placeholders (e.g. {@code "1"})</li>
     *   <li>Using string literals that resemble {@code null}</li>
     * </ul>
     *
     * @return a stream of invalid boolean property key/value pairs
     */
    private static Stream<Arguments> invalidBooleanProvider() {
        return Stream.of(
                Arguments.of("mail.smtp.auth", "yes"),
                Arguments.of("mail.smtp.auth", "1"),
                Arguments.of("mail.smtp.starttls.enable", "enabled"),
                Arguments.of("mail.smtp.starttls.enable", "null")
        );
    }

    /**
     * Verifies fail-fast behavior when integer-based SMTP properties
     * are {@code null}, empty, or contain only whitespace.
     *
     * <p>
     * Integer SMTP properties such as timeouts must be provided as
     * non-empty positive integers. Blank or missing values indicate
     * an invalid configuration and must be rejected during startup.
     * </p>
     *
     * <p>
     * This test ensures that:
     * </p>
     * <ul>
     *   <li>Empty or whitespace-only values are not accepted.</li>
     *   <li>The configuration fails immediately with a deterministic error.</li>
     *   <li>Ambiguous defaults or runtime parsing errors are avoided.</li>
     * </ul>
     *
     * @param key   the integer SMTP property key being validated
     * @param value the blank or {@code null} value supplied
     */
    @ParameterizedTest(name = "Blank integer value for {0}")
    @MethodSource("blankIntegerProvider")
    @DisplayName("Should fail fast when integer SMTP property is null or blank")
    void shouldFailWhenIntegerPropertyIsBlank(String key, String value) {
        // given
        final Map<String, String> props = constructValidMailProperties();
        props.put(key, value);
        final MailConfigurations configuration = constructConfigurationWith(props);

        // when / then
        final IllegalStateException ex =
                assertThrows(IllegalStateException.class, configuration::javaMailSender);
        assertEquals(
                "Mail property '" + key + "' must be a non-empty positive integer",
                ex.getMessage()
        );
    }

    /**
     * Supplies blank or {@code null} values for integer SMTP properties.
     *
     * <p>
     * These inputs simulate common configuration errors such as:
     * </p>
     * <ul>
     *   <li>Leaving timeout values empty in YAML or properties files</li>
     *   <li>Accidentally removing a value while keeping the key</li>
     * </ul>
     *
     * @return a stream of integer property keys with blank or {@code null} values
     */
    private static Stream<Arguments> blankIntegerProvider() {
        return Stream.of(
                Arguments.of("mail.smtp.timeout", ""),
                Arguments.of("mail.smtp.timeout", "   "),
                Arguments.of("mail.smtp.connectiontimeout", null),
                Arguments.of("mail.smtp.writetimeout", "")
        );
    }

    /**
     * Verifies fail-fast behavior when integer SMTP properties
     * contain non-numeric values.
     *
     * <p>
     * Integer SMTP properties must be parsable as base-10 integers.
     * Any non-numeric value would cause runtime failures in the
     * underlying JavaMail implementation and must be rejected early.
     * </p>
     *
     * <p>
     * This test ensures that:
     * </p>
     * <ul>
     *   <li>Alphabetic or decimal values are rejected.</li>
     *   <li>Misleading numeric formats (e.g. {@code "1_000"}) are not accepted.</li>
     *   <li>Clear error messages are produced during application startup.</li>
     * </ul>
     *
     * @param key   the integer SMTP property key being validated
     * @param value the non-numeric value supplied
     */
    @ParameterizedTest(name = "Non-numeric integer value for {0} = {1}")
    @MethodSource("invalidIntegerProvider")
    @DisplayName("Should fail fast when integer SMTP property is not a number")
    void shouldFailWhenIntegerPropertyIsNotANumber(String key, String value) {
        // given
        final Map<String, String> props = constructValidMailProperties();
        props.put(key, value);
        final MailConfigurations configuration = constructConfigurationWith(props);

        // when / then
        final IllegalStateException ex =
                assertThrows(IllegalStateException.class, configuration::javaMailSender);
        assertEquals(
                "Mail property '" + key + "' must be a valid integer",
                ex.getMessage()
        );
    }

    /**
     * Supplies non-numeric values for integer SMTP properties.
     *
     * <p>
     * These values intentionally violate numeric constraints and
     * represent realistic misconfiguration scenarios.
     * </p>
     *
     * @return a stream of integer property keys with non-numeric values
     */
    private static Stream<Arguments> invalidIntegerProvider() {
        return Stream.of(
                Arguments.of("mail.smtp.timeout", "abc"),
                Arguments.of("mail.smtp.timeout", "12.5"),
                Arguments.of("mail.smtp.connectiontimeout", "ten"),
                Arguments.of("mail.smtp.writetimeout", "1_000")
        );
    }

    /**
     * Verifies fail-fast behavior when integer SMTP properties
     * contain zero or negative values.
     *
     * <p>
     * Timeout-related SMTP properties must be strictly positive integers.
     * Zero or negative values are semantically invalid and may cause
     * undefined behavior in mail transport.
     * </p>
     *
     * <p>
     * This test ensures that:
     * </p>
     * <ul>
     *   <li>Zero values are rejected.</li>
     *   <li>Negative values are rejected.</li>
     *   <li>Configuration errors are detected at startup, not at runtime.</li>
     * </ul>
     *
     * @param key   the integer SMTP property key being validated
     * @param value the non-positive value supplied
     */
    @ParameterizedTest(name = "Non-positive integer value for {0} = {1}")
    @MethodSource("nonPositiveIntegerProvider")
    @DisplayName("Should fail fast when integer SMTP property is zero or negative")
    void shouldFailWhenIntegerPropertyIsNonPositive(String key, String value) {
        // given
        final Map<String, String> props = constructValidMailProperties();
        props.put(key, value);

        final MailConfigurations configuration = constructConfigurationWith(props);

        // when / then
        final IllegalStateException ex =
                assertThrows(IllegalStateException.class, configuration::javaMailSender);

        assertEquals(
                "Mail property '" + key + "' must be a positive integer",
                ex.getMessage()
        );
    }

    /**
     * Verifies that a warning is logged when an unknown SMTP property is detected.
     *
     * <p>
     * This test ensures that the mail configuration performs <strong>non-fatal
     * validation</strong> for unrecognized SMTP properties. Unknown properties
     * should not prevent application startup, but must be surfaced via a
     * warning log to help detect configuration mistakes or typos.
     * </p>
     *
     * <p>
     * The test:
     * </p>
     * <ul>
     *   <li>Creates a valid SMTP configuration</li>
     *   <li>Adds a single unknown SMTP property</li>
     *   <li>Invokes {@link MailConfigurations#javaMailSender()}</li>
     *   <li>Captures log output using a Logback {@link ListAppender}</li>
     * </ul>
     *
     * <p>
     * The assertion verifies that:
     * </p>
     * <ul>
     *   <li>Exactly one warning log entry is produced</li>
     *   <li>The log level is {@code WARN}</li>
     *   <li>The log message clearly identifies the unknown property</li>
     * </ul>
     *
     * <p>
     * This behavior provides early visibility into configuration issues while
     * preserving forward compatibility with JavaMail extensions and
     * vendor-specific SMTP settings.
     * </p>
     */
    @DisplayName("Should log warning when unknown SMTP properties are detected")
    @Test
    void shouldLogWarningForUnknownSmtpProperties() {
        // given
        final Map<String, String> props = new HashMap<>(constructValidMailProperties());
        props.put("mail.smtp.unknown.property", "value");
        final MailConfigurations configuration = constructConfigurationWith(props);

        final Logger logger = (Logger) LoggerFactory.getLogger(MailConfigurations.class);
        logger.setLevel(Level.WARN);
        logger.setAdditive(false);

        final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        // when
        configuration.javaMailSender();

        // then
        List<ILoggingEvent> logs = appender.list;
        assertEquals(1, logs.size());
        assertEquals(Level.WARN, logs.get(0).getLevel());
        assertEquals(
                "Unknown SMTP property detected: mail.smtp.unknown.property",
                logs.get(0).getFormattedMessage()
        );
    }

    /**
     * Supplies zero and negative values for integer SMTP properties.
     *
     * <p>
     * These values violate the positive-integer constraint required
     * for timeout configuration and must be rejected.
     * </p>
     *
     * @return a stream of integer property keys with non-positive values
     */
    private static Stream<Arguments> nonPositiveIntegerProvider() {
        return Stream.of(
                Arguments.of("mail.smtp.timeout", "0"),
                Arguments.of("mail.smtp.timeout", "-1"),
                Arguments.of("mail.smtp.connectiontimeout", "-500"),
                Arguments.of("mail.smtp.writetimeout", "0")
        );
    }

    /**
     * Returns a mutable map of valid SMTP properties for use in unit tests.
     *
     * <p>
     * Mutability is intentional here, as most tests modify individual properties
     * to verify fail-fast validation behavior. Using a mutable map avoids
     * unnecessary intermediate copies and keeps test setup concise and explicit.
     * </p>
     */
    private Map<String, String> constructValidMailProperties() {
        final Map<String, String> props = new HashMap<>();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.connectiontimeout", "5000");
        props.put("mail.smtp.timeout", "3000");
        props.put("mail.smtp.writetimeout", "5000");
        return props;
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
}
