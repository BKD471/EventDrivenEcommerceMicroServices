package com.forsaken.ecommerce.notification.configs.mail;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.Properties;

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
    public static final String[] REQUIRED_SMTP_KEYS = {
            "mail.smtp.auth",
            "mail.smtp.starttls.enable",
            "mail.smtp.connectiontimeout",
            "mail.smtp.timeout",
            "mail.smtp.writetimeout"
    };

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
     * This method performs comprehensive fail-fast validation on the configured
     * JavaMail SMTP properties before they are applied to the {@link JavaMailSender}.
     * </p>
     *
     * <h3>Validation rules</h3>
     * <ul>
     *   <li>The properties map must not be {@code null}.</li>
     *   <li>The properties map must not be empty.</li>
     *   <li>All required SMTP keys defined in {@code REQUIRED_SMTP_KEYS} must be present.</li>
     *   <li>Boolean properties (for example {@code mail.smtp.auth} and
     *       {@code mail.smtp.starttls.enable}) must have values {@code true} or {@code false}
     *       (case-insensitive).</li>
     *   <li>Timeout-related properties (for example
     *       {@code mail.smtp.connectiontimeout}, {@code mail.smtp.timeout},
     *       {@code mail.smtp.writetimeout}) must be valid positive integers.</li>
     * </ul>
     *
     * <h3>Fail-fast behavior</h3>
     * <p>
     * Any validation failure results in an {@link IllegalStateException} being thrown
     * during application startup. This prevents the application from running with
     * invalid or partially configured mail settings and avoids obscure runtime failures
     * inside the JavaMail implementation.
     * </p>
     *
     * @return a validated map of JavaMail session properties
     * @throws IllegalStateException if any required property is missing or invalid
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
        validateBoolean(mailProps, "mail.smtp.auth");
        validateBoolean(mailProps, "mail.smtp.starttls.enable");
        validatePositiveInteger(mailProps, "mail.smtp.connectiontimeout");
        validatePositiveInteger(mailProps, "mail.smtp.timeout");
        validatePositiveInteger(mailProps, "mail.smtp.writetimeout");
        return mailProps;
    }

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
    private void validateBoolean(
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
     * This method is intended for SMTP timeout-related properties such as
     * {@code mail.smtp.timeout}, {@code mail.smtp.connectiontimeout}, and
     * {@code mail.smtp.writetimeout}.
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
    private void validatePositiveInteger(
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
}
