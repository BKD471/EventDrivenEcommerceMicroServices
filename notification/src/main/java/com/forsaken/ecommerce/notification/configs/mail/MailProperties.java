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
 * Example configuration:
 * </p>
 *
 * <pre>
 * mail:
 *   properties:
 *     mail:
 *       smtp:
 *         auth: true
 *         starttls:
 *           enabled: true
 *         ssl:
 *           trust: "*"
 *         connectiontimeout: 5000
 *         timeout: 3000
 *         writetimeout: 5000
 * </pre>
 *
 * <p><strong>Note:</strong></p>
 * <ul>
 *   <li>{@code mail.smtp.ssl.trust} is the correct JavaMail property for
 *       trusting SSL certificates.</li>
 *   <li>{@code mail.smtp.trust} is <strong>not</strong> a valid JavaMail key
 *       and will be ignored.</li>
 * </ul>
 *
 * <p>
 * Using {@code "*"} should be limited to development or test environments.
 * </p>
 */
@Validated
@ConfigurationProperties(prefix = "spring.mail")
public record MailProperties(

        /**
         * SMTP server host name or IP address.
         * <p>
         * This value identifies the mail server to which the application will connect
         * when sending email messages.
         * </p>
         *
         * @throws jakarta.validation.ConstraintViolationException if blank or missing
         */
        @NotBlank(message = "Mail host must not be blank")
        String host,

        /**
         * SMTP server port.
         * <p>
         * Common values include:
         * <ul>
         *   <li>{@code 25} – Standard SMTP (unencrypted)</li>
         *   <li>{@code 587} – SMTP with STARTTLS</li>
         *   <li>{@code 465} – SMTP over SSL</li>
         * </ul>
         * </p>
         *
         * @throws jakarta.validation.ConstraintViolationException if null or less than {@code 1}
         */
        @NotNull(message = "Mail port must be provided")
        @Min(value = 1, message = "Mail port must be greater than 0")
        Integer port,

        /**
         * Username used for SMTP authentication.
         * <p>
         * Required when SMTP authentication is enabled.
         * </p>
         */
        @NotBlank(message = "Mail username must not be blank")
        String username,

        /**
         * Password used for SMTP authentication.
         * <p>
         * This value should be provided securely via environment variables,
         * Spring Cloud Config, or a secrets manager in production environments
         * and should not be hardcoded in version-controlled configuration files.
         * </p>
         */
        @NotBlank(message = "Mail password must not be blank")
        String password,

        /**
         * Additional JavaMail SMTP properties.
         *
         * <p>
         * This map contains protocol-level SMTP configuration required by the
         * underlying JavaMail sender (for example authentication flags, TLS settings,
         * and timeout values).
         * </p>
         *
         * <p><strong>Validation rules:</strong></p>
         * <ul>
         *   <li>Must not be {@code null}.</li>
         *   <li>Must not be empty.</li>
         * </ul>
         *
         * <p>
         * These constraints are enforced at configuration binding time using
         * {@link jakarta.validation.constraints.NotEmpty} to ensure fail-fast
         * application startup when mail configuration is missing or incomplete.
         * </p>
         *
         * <p>
         * Additional semantic validation (such as required keys, boolean values,
         * and positive integer constraints) is performed explicitly in
         * {@link MailConfigurations} to provide clear and actionable error messages.
         * </p>
         */
        @NotEmpty(message = "Mail properties must not be null or empty")
        Map<String, String> properties
) {
}
