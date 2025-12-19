package com.forsaken.ecommerce.notification.configs.mail;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

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
    private static final String[] REQUIRED_SMTP_KEYS = {
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
     * This method ensures:
     * </p>
     * <ul>
     *   <li>The properties map is not {@code null}.</li>
     *   <li>The properties map is not empty.</li>
     *   <li>All required SMTP keys are present.</li>
     * </ul>
     *
     * <p>
     * Any validation failure results in an {@link IllegalStateException}
     * with a descriptive error message.
     * </p>
     *
     * @return a validated map of JavaMail session properties
     * @throws IllegalStateException if validation fails
     */
    private Map<String, String> constructPropertiesMap() {
        final Map<String, String> mailProps = mailProperties.properties();
        if (null == mailProps || mailProps.isEmpty()) {
            throw new IllegalStateException("Mail properties must not be empty. " +
                    "Please configure required SMTP settings (e.g. authentication and TLS).");
        }
        for (final String key : REQUIRED_SMTP_KEYS) {
            if (!mailProps.containsKey(key)) {
                throw new IllegalStateException("Missing required mail property: " + key);
            }
        }
        return mailProps;
    }
}
