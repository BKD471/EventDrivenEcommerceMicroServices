package com.forsaken.ecommerce.notification.configs.mail;


import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.Map;

/**
 * Configuration properties for mail support in the notification service.
 * <p>
 * This record binds external configuration (YAML / properties / Config Server /
 * environment variables) under the {@code spring.mail.*} namespace and provides
 * a type-safe, validated representation of mail-related settings.
 * </p>
 *
 * <p>
 * These properties are typically used to configure a {@link org.springframework.mail.javamail.JavaMailSender}
 * instance for sending emails via SMTP.
 * </p>
 *
 * <h2>Configuration Prefix</h2>
 * <pre>
 * spring.mail
 * </pre>
 *
 * <h2>Example Configuration</h2>
 * <pre>{@code
 * spring:
 *   mail:
 *     host: localhost
 *     port: 1025
 *     username: admin
 *     password: admin
 *     properties:
 *       mail.smtp.auth: true
 *       mail.smtp.starttls.enable: true
 *       mail.smtp.trust: "*"
 * }</pre>
 *
 * <p><strong>Note:</strong> The {@code trust} property shown in the example above is
 * optional and is <em>not</em> part of the required SMTP keys. It is typically only
 *
 * <p><strong>Note:</strong> The {@code trust} property shown in the example above is
 * optional and is <em>not</em> part of the required SMTP keys. It is typically only
 * needed in specific scenarios, such as local development or testing with
 * self-signed certificates, where you want to trust all SSL certificates
 * (for example, using {@code trust="*"}). In production, you should avoid
 * blindly trusting all certificates and instead rely on a proper trust store.</p>
 *
 * <h2>Validation</h2>
 * <ul>
 *   <li>Fails fast at application startup if mandatory properties are missing or invalid.</li>
 *   <li>Ensures correctness before attempting to create or use mail infrastructure.</li>
 * </ul>
 *
 * <p>
 * Validation is enabled via {@link org.springframework.validation.annotation.Validated}
 * and Jakarta Bean Validation constraints.
 * </p>
 *
 * @see org.springframework.boot.context.properties.ConfigurationProperties
 * @see org.springframework.mail.javamail.JavaMailSender
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
        @NotBlank(message = "Mail password must not be blank and should be supplied via environment variables, Spring Cloud Config, or a secrets manager (avoid hardcoding in configuration files)")
        String password,

        /**
         * Additional JavaMail session properties.
         * <p>
         * These properties are passed directly to the underlying JavaMail implementation and
         * are typically used to configure protocol-specific options such as SMTP authentication
         * and TLS settings.
         * </p>
         *
         * <p><strong>Examples</strong> (using the {@code spring.mail.properties.*} prefix):</p>
         * <ul>
         *     <li>{@code spring.mail.properties.mail.smtp.auth=true}</li>
         *     <li>{@code spring.mail.properties.mail.smtp.starttls.enable=true}</li>
         *     <li>{@code spring.mail.properties.mail.debug=false}</li>
         * </ul>
         *
         * <p><strong>Constraints</strong>:</p>
         * <ul>
         *     <li>Must not be {@code null} (enforced by {@link jakarta.validation.constraints.NotNull}).</li>
         *     <li>Keys and values should be valid JavaMail properties supported by the configured mail protocol.</li>
         * </ul>
         *
         * @implNote Properties are bound from configuration using the {@code spring.mail.properties.*} prefix
         * and then flattened into this map.
         */
        @NotNull(message = "Mail properties must not be null")
        Map<String, String> properties
) {
}
