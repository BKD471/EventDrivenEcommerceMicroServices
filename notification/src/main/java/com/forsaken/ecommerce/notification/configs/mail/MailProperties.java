package com.forsaken.ecommerce.notification.configs.mail;


import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.Map;

/**
 * JavaMail SMTP properties passed directly to the underlying JavaMail Session.
 *
 * <p>
 * This record represents SMTP configuration bound from {@code spring.mail.*}
 * and is used to configure the {@link org.springframework.mail.javamail.JavaMailSender}.
 * </p>
 *
 * <h2>Example configuration</h2>
 *
 * <p>
 * Properties are defined using the flat JavaMail property naming convention
 * and passed directly to the JavaMail {@code Session}.
 * </p>
 *
 * <pre>
 * spring:
 *   mail:
 *     host: localhost
 *     port: 1025
 *     username: ${EMAIL_USERNAME:}
 *     password: ${EMAIL_PASSWORD:}
 *     properties:
 *       mail.smtp.auth: true
 *       mail.smtp.starttls.enable: true
 *       mail.smtp.connectiontimeout: 5000
 *       mail.smtp.timeout: 3000
 *       mail.smtp.writetimeout: 5000
 * </pre>
 *
 * <h2>Validation model</h2>
 * <ul>
 *   <li>Structural validation (non-null, non-empty) is enforced at configuration
 *       binding time using Bean Validation annotations.</li>
 *   <li>Semantic validation (required keys, boolean correctness, positive integer
 *       constraints) is enforced explicitly in {@link MailConfigurations}.</li>
 * </ul>
 *
 * <h2>Security considerations</h2>
 * <ul>
 *   <li>SMTP credentials should be supplied via environment variables, secrets
 *       managers, or externalized configuration in non-local environments.</li>
 *   <li>In production, SSL/TLS certificate validation should rely on proper
 *       trust stores rather than permissive trust overrides.</li>
 *   <li>Properties such as {@code mail.smtp.ssl.trust="*"} should be avoided
 *       outside local development or test environments.</li>
 * </ul>
 *
 * <p>
 * This design follows Spring Boot and JavaMail best practices by enforcing
 * a small, critical set of required SMTP properties while allowing optional
 * JavaMail extensions to pass through transparently.
 * </p>
 */
@Validated
@ConfigurationProperties(prefix = "spring.mail")
public record MailProperties(

        /**
         * SMTP server host name or IP address.
         *
         * <p>
         * Identifies the mail server to which the application will connect
         * when sending email messages.
         * </p>
         *
         * @throws jakarta.validation.ConstraintViolationException if blank or missing
         */
        @NotBlank(message = "Mail host must not be blank")
        String host,

        /**
         * SMTP server port.
         *
         * <p>
         * Common values include:
         * </p>
         * <ul>
         *   <li>{@code 25} – Standard SMTP (unencrypted)</li>
         *   <li>{@code 587} – SMTP with STARTTLS</li>
         *   <li>{@code 465} – SMTP over SSL</li>
         * </ul>
         *
         * @throws jakarta.validation.ConstraintViolationException if null or less than {@code 1}
         */
        @NotNull(message = "Mail port must be provided")
        @Min(value = 1, message = "Mail port must be greater than 0")
        Integer port,

        /**
         * Username used for SMTP authentication.
         *
         * <p>
         * Required when {@code mail.smtp.auth=true}.
         * </p>
         */
        @NotBlank(message = "Mail username must not be blank")
        String username,

        /**
         * Password used for SMTP authentication.
         *
         * <p>
         * This value should be provided securely via environment variables,
         * Spring Cloud Config, or a secrets manager and should not be
         * hardcoded in version-controlled configuration files.
         * </p>
         */
        @NotBlank(message = "Mail password must not be blank")
        String password,

        /**
         * Additional JavaMail SMTP session properties.
         *
         * <p>
         * This map contains protocol-level configuration consumed directly by
         * JavaMail (authentication flags, TLS settings, timeout values, etc.).
         * </p>
         *
         * <p><strong>Constraints:</strong></p>
         * <ul>
         *   <li>Must not be {@code null}</li>
         *   <li>Must not be empty</li>
         * </ul>
         *
         * <p>
         * Further validation of required keys and value correctness is performed
         * explicitly in {@link MailConfigurations} to ensure fail-fast startup
         * and clear error reporting.
         * </p>
         */
        @NotEmpty(message = "Mail properties must not be null or empty")
        Map<String, String> properties
) {
}
