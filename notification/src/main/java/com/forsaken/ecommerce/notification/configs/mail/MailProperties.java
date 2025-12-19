package com.forsaken.ecommerce.notification.configs.mail;


import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.Map;

@Validated
@ConfigurationProperties(prefix = "spring.mail")
public record MailProperties(

        @NotBlank(message = "Mail host must not be blank")
        String host,

        @NotNull(message = "Mail port must be provided")
        @Min(value = 1, message = "Mail port must be greater than 0")
        Integer port,

        @NotBlank(message = "Mail username must not be blank")
        String username,

        @NotBlank(message = "Mail password must not be blank")
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
