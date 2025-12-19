package com.forsaken.ecommerce.notification.configs.mail;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Mail configuration for the notification service.
 *
 * <p>
 * This configuration class is responsible for creating and configuring
 * a {@link JavaMailSender} using externally supplied SMTP settings
 * bound via {@link MailProperties}.
 * </p>
 *
 * <p>
 * All mail-related configuration is expected to be provided under the
 * {@code spring.mail.*} namespace and validated before the application
 * begins processing requests.
 * </p>
 *
 * <h2>Fail-fast configuration validation</h2>
 * <p>
 * This configuration enforces the presence of required JavaMail SMTP
 * properties at startup. If any mandatory property is missing or the
 * properties map is empty, application startup will fail immediately
 * with a clear and actionable error message.
 * </p>
 *
 * <p>
 * This approach prevents subtle runtime failures such as silent mail
 * delivery errors, misconfigured TLS, or authentication issues.
 * </p>
 *
 * <h2>Responsibilities</h2>
 * <ul>
 *   <li>Create a {@link JavaMailSenderImpl} instance.</li>
 *   <li>Apply SMTP connection parameters (host, port, username, password).</li>
 *   <li>Apply JavaMail session properties.</li>
 *   <li>Validate the presence of required SMTP configuration keys.</li>
 * </ul>
 *
 * @see MailProperties
 * @see JavaMailSender
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class MailConfigurations {

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
    public static final Set<String> REQUIRED_SMTP_KEYS = Set.of(
            "mail.smtp.auth",
            "mail.smtp.starttls.enable",
            "mail.smtp.connectiontimeout",
            "mail.smtp.timeout",
            "mail.smtp.writetimeout"
    );

    /**
     * Set of known SMTP property prefixes recognized by this application.
     *
     * <p>
     * This set is derived from {@link #REQUIRED_SMTP_KEYS} and represents the
     * authoritative list of SMTP property namespaces that are considered valid.
     * It is used exclusively for <strong>non-fatal validation</strong> to detect
     * potentially misconfigured or unsupported SMTP properties.
     * </p>
     *
     * <p>
     * The prefixes are generated using {@link #deriveKnownPrefixes()}, which
     * intentionally allows hierarchical property structures for certain SMTP
     * settings (for example TLS or SSL configuration), while keeping other
     * properties strictly defined.
     * </p>
     *
     * <h3>Purpose</h3>
     * <ul>
     *   <li>Warn about unknown or misspelled SMTP properties</li>
     *   <li>Preserve forward compatibility with JavaMail extensions</li>
     *   <li>Centralize SMTP namespace awareness in one location</li>
     * </ul>
     *
     * <p>
     * Properties whose keys do not start with any of these prefixes will trigger
     * a warning during application startup, but will not prevent the application
     * from starting.
     * </p>
     *
     * <h3>Example</h3>
     * <pre>{@code
     * Known prefix:   mail.smtp.starttls.
     * Accepted key:   mail.smtp.starttls.enable
     * Rejected key:   mail.smtp.starttls.foo   (warned)
     *
     * Known prefix:   mail.smtp.timeout
     * Accepted key:   mail.smtp.timeout
     * Rejected key:   mail.smtp.timeout.extra  (warned)
     * }</pre>
     *
     * <p>
     * This design enforces strict validation for required SMTP settings while
     * remaining flexible enough to support advanced JavaMail configuration.
     * </p>
     */
    private static final Set<String> KNOWN_SMTP_PREFIXES = deriveKnownPrefixes();

    /**
     * Validated, immutable mail configuration properties bound from
     * {@code spring.mail.*}.
     */
    private final MailProperties mailProperties;

    /**
     * Creates and configures the {@link JavaMailSender} bean.
     *
     * <p>
     * This method applies SMTP connection parameters and validated
     * JavaMail session properties to a {@link JavaMailSenderImpl}.
     * </p>
     *
     * <p>
     * The method performs additional runtime validation to ensure that
     * required SMTP properties are present. If validation fails, application
     * startup is aborted.
     * </p>
     *
     * @return a fully configured {@link JavaMailSender}
     * @throws IllegalStateException if required SMTP properties are missing
     */
    @Bean
    public JavaMailSender javaMailSender() {
        final JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(mailProperties.host());
        sender.setPort(mailProperties.port());
        sender.setUsername(mailProperties.username());
        sender.setPassword(mailProperties.password());

        final Properties properties = new Properties();
        final Map<String, String> mailProps = constructPropertiesMap();
        properties.putAll(mailProps);
        sender.setJavaMailProperties(properties);
        return sender;
    }

    /**
     * Validates and returns the JavaMail session properties.
     *
     * <p>
     * This method performs strict validation on a required subset of SMTP
     * properties that are critical for correct mail delivery:
     * </p>
     *
     * <ul>
     *   <li>Presence of all required SMTP keys</li>
     *   <li>Boolean validation for authentication and STARTTLS flags</li>
     *   <li>Positive integer validation for timeout-related properties</li>
     * </ul>
     *
     * <p>
     * Any additional JavaMail properties provided via configuration are
     * intentionally allowed and passed through without validation. This
     * preserves compatibility with advanced JavaMail features and
     * vendor-specific SMTP extensions.
     * </p>
     *
     * <p>
     * This design follows Spring Boot and JavaMail conventions, where only
     * a core set of critical properties are validated while allowing
     * extensibility for optional settings.
     * </p>
     *
     * @return a validated map of JavaMail session properties
     * @throws IllegalStateException if required validation fails
     */
    private Map<String, String> constructPropertiesMap() {
        final Map<String, String> mailProps = mailProperties.properties();
        if (null == mailProps || mailProps.isEmpty()) {
            throw new IllegalStateException(
                    "Mail properties must not be empty. " +
                            "Required SMTP properties: " + String.join(", ", REQUIRED_SMTP_KEYS)
            );
        }
        for (final String key : REQUIRED_SMTP_KEYS) {
            if (!mailProps.containsKey(key)) {
                throw new IllegalStateException("Missing required mail property: " + key);
            }
        }
        SMTP_VALIDATORS.values().forEach(validator -> validator.accept(mailProps));

        warnOnUnknownSmtpProperties(mailProps);
        return mailProps;
    }

    private static final Map<String, Consumer<Map<String, String>>> SMTP_VALIDATORS = Map.of(
            "mail.smtp.auth",
            props -> validateBoolean(props, "mail.smtp.auth"),

            "mail.smtp.starttls.enable",
            props -> validateBoolean(props, "mail.smtp.starttls.enable"),

            "mail.smtp.connectiontimeout",
            props -> validatePositiveInteger(props, "mail.smtp.connectiontimeout"),

            "mail.smtp.timeout",
            props -> validatePositiveInteger(props, "mail.smtp.timeout"),

            "mail.smtp.writetimeout",
            props -> validatePositiveInteger(props, "mail.smtp.writetimeout")
    );

    /**
     * Validates that a mail configuration property represents a valid boolean value.
     * <p>
     * This method enforces strict boolean semantics for SMTP-related properties
     * (for example {@code mail.smtp.auth} or {@code mail.smtp.starttls.enable}).
     * Only the values {@code "true"} or {@code "false"} (case-insensitive) are accepted.
     * </p>
     *
     * <p>
     * Any other value (including {@code null}, empty strings, or arbitrary text)
     * is considered invalid and results in immediate application startup failure.
     * </p>
     *
     * <h3>Fail-fast behavior</h3>
     * <p>
     * This validation prevents obscure runtime failures caused by invalid
     * boolean values being silently accepted and later misinterpreted by
     * the JavaMail implementation.
     * </p>
     *
     * @param props the resolved mail properties map
     * @param key   the property key to validate
     * @throws IllegalStateException if the property value is not a valid boolean
     */
    private static void validateBoolean(
            final Map<String, String> props,
            final String key
    ) {
        final String value = props.get(key);
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(
                    "Mail property '" + key + "' must not be null and must be either 'true' or 'false'"
            );
        }
        if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
            throw new IllegalStateException(
                    "Invalid boolean value for mail property '" + key + "': " + value
            );
        }
    }

    /**
     * Validates that a mail configuration property represents a positive integer.
     * <p>
     * This method is intended for SMTP timeout-related properties (e.g.
     * {@code mail.smtp.timeout}, {@code mail.smtp.connectiontimeout}, and
     * {@code mail.smtp.writetimeout}).
     * </p>
     *
     * <p>
     * The property value must:
     * </p>
     * <ul>
     *   <li>Be parseable as an integer</li>
     *   <li>Be greater than zero</li>
     * </ul>
     *
     * <p>
     * Values that are negative, zero, non-numeric, or missing will cause
     * application startup to fail immediately.
     * </p>
     *
     * <h3>Fail-fast behavior</h3>
     * <p>
     * This validation avoids late-stage runtime errors that would otherwise
     * occur when JavaMail attempts to interpret invalid timeout values.
     * </p>
     *
     * @param props the resolved mail properties map
     * @param key   the property key to validate
     * @throws IllegalStateException if the property value is not a valid positive integer
     */
    private static void validatePositiveInteger(
            final Map<String, String> props,
            final String key
    ) {
        final String rawValue = props.get(key);
        if (!StringUtils.hasText(rawValue)) {
            throw new IllegalStateException(
                    "Mail property '" + key + "' must be a non-empty positive integer"
            );
        }
        try {
            final int value = Integer.parseInt(rawValue);
            if (value <= 0) {
                throw new IllegalStateException(
                        "Mail property '" + key + "' must be a positive integer"
                );
            }
        } catch (NumberFormatException ex) {
            throw new IllegalStateException(
                    "Mail property '" + key + "' must be a valid integer",
                    ex
            );
        }
    }

    /**
     * Logs warnings for SMTP properties that are not recognized by this application.
     *
     * <p>
     * This method performs a <em>non-fatal</em> validation pass over all configured
     * JavaMail SMTP properties. Any property whose key does not match a known
     * SMTP prefix is considered unknown and will trigger a warning.
     * </p>
     *
     * <p>
     * Known prefixes are derived from {@link #REQUIRED_SMTP_KEYS} to ensure a
     * single source of truth for supported SMTP configuration. This allows:
     * </p>
     * <ul>
     *   <li>Strict enforcement of required SMTP properties</li>
     *   <li>Extensibility for structured JavaMail properties (for example nested
     *       TLS or SSL configuration)</li>
     *   <li>Early visibility into potential misconfiguration or typos</li>
     * </ul>
     *
     * <p>
     * Unknown properties are <strong>not rejected</strong> to preserve compatibility
     * with advanced or vendor-specific JavaMail extensions. Instead, a warning
     * is logged to assist operators and developers.
     * </p>
     *
     * <h3>Example</h3>
     * <pre>{@code
     * mail.smtp.starttls.enable      -> allowed
     * mail.smtp.timeout              -> allowed
     * mail.smtp.unknown.setting      -> logged as warning
     * }</pre>
     *
     * @param mailProps resolved JavaMail SMTP properties
     */
    private void warnOnUnknownSmtpProperties(final Map<String, String> mailProps) {
        mailProps.keySet().forEach(key -> {
            if (KNOWN_SMTP_PREFIXES.stream().noneMatch(key::startsWith)) {
                log.warn("Unknown SMTP property detected: {}", key);
            }
        });
    }

    /**
     * Derives the set of known SMTP property prefixes from
     * {@link #REQUIRED_SMTP_KEYS}.
     *
     * <p>
     * This method ensures that required SMTP properties remain the
     * <strong>single source of truth</strong> for both:
     * </p>
     * <ul>
     *   <li>Mandatory configuration enforcement</li>
     *   <li>Recognition of valid JavaMail property namespaces</li>
     * </ul>
     *
     * <p>
     * Certain SMTP properties are hierarchical by nature (for example
     * {@code mail.smtp.starttls.enable}). For such properties, this method
     * allows their parent prefix (e.g. {@code mail.smtp.starttls.}) so that
     * related sub-properties can be accepted without triggering warnings.
     * </p>
     *
     * <p>
     * Flat properties (such as timeout values) are treated as exact matches
     * and do not allow additional sub-keys.
     * </p>
     *
     * <h3>Derivation rules</h3>
     * <ul>
     *   <li>{@code mail.smtp.starttls.enable} → {@code mail.smtp.starttls.}</li>
     *   <li>{@code mail.smtp.auth} → {@code mail.smtp.auth}</li>
     * </ul>
     *
     * <p>
     * This strategy provides a balance between strict validation and forward
     * compatibility with JavaMail extensions.
     * </p>
     *
     * @return an unmodifiable set of known SMTP property prefixes
     */
    private static Set<String> deriveKnownPrefixes() {
        return REQUIRED_SMTP_KEYS.stream()
                .map(key -> {
                    // allow hierarchy only for structured keys
                    if (key.endsWith(".enable") || key.endsWith(".trust")) {
                        return key.substring(0, key.lastIndexOf('.')) + ".";
                    }
                    return key;
                })
                .collect(Collectors.toUnmodifiableSet());
    }
}
