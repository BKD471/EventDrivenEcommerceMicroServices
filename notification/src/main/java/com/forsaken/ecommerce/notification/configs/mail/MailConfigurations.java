package com.forsaken.ecommerce.notification.configs.mail;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

/**
 * Mail configuration for the notification service.
 * <p>
 * This configuration class creates and configures a {@link JavaMailSender}
 * bean using externally supplied mail settings bound via
 * {@link MailProperties}.
 * </p>
 *
 * <p>
 * All SMTP connection details (host, port, credentials, and protocol-level
 * properties) are provided through {@code spring.mail.*} configuration
 * and validated at application startup.
 * </p>
 *
 * <h2>Responsibilities</h2>
 * <ul>
 *   <li>Instantiate a {@link JavaMailSender} implementation.</li>
 *   <li>Apply SMTP connection details such as host, port, username, and password.</li>
 *   <li>Forward additional JavaMail session properties (TLS, auth, timeouts, etc.).</li>
 * </ul>
 *
 * <p>
 * This configuration does <strong>not</strong> perform any runtime logic and
 * exists purely to wire infrastructure components.
 * </p>
 *
 * @see MailProperties
 * @see JavaMailSender
 */
@Configuration
@RequiredArgsConstructor
public class MailConfigurations {

    /**
     * Validated, immutable mail configuration properties.
     */
    private final MailProperties mailProperties;

    /**
     * Creates and configures a {@link JavaMailSender} bean.
     * <p>
     * The returned {@link JavaMailSender} is fully configured using the
     * {@link MailProperties} record and is suitable for injection into
     * application services that send email.
     * </p>
     *
     * <p>
     * Additional JavaMail session properties (for example, SMTP authentication,
     * STARTTLS, connection timeouts, and debug flags) are applied directly to
     * the underlying JavaMail session.
     * </p>
     *
     * @return a fully configured {@link JavaMailSender} instance
     */
    @Bean
    public JavaMailSender javaMailSender() {
        final JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(mailProperties.host());
        sender.setPort(mailProperties.port());
        sender.setUsername(mailProperties.username());
        sender.setPassword(mailProperties.password());

        final Properties properties = new Properties();
        final var mailProps = mailProperties.properties();
        if (mailProps == null || mailProps.isEmpty()) {
            throw new IllegalStateException("Mail properties must not be empty. Please configure required SMTP settings (e.g. authentication and TLS).");
        }
        properties.putAll(mailProps);
        sender.setJavaMailProperties(properties);
        return sender;
    }
}
