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
         * Arbitrary JavaMail properties (flattened automatically).
         * Example: mail.smtp.auth, mail.smtp.starttls.enabled, etc.
         */
        @NotNull(message = "Mail properties must not be null")
        Map<String, String> properties
) {
}
