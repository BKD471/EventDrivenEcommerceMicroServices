package com.forsaken.ecommerce.notification.configs.mail;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class MailConfigurationsTest {

    @Test
    @DisplayName("Should create JavaMailSender with all SMTP properties set correctly")
    void shouldCreateJavaMailSenderSuccessfully() {
        // Given
        final MailProperties mailProperties = new MailProperties(
                "localhost",
                1025,
                "admin",
                "admin",
                Map.of(
                        "mail.smtp.auth", "true",
                        "mail.smtp.starttls.enable", "true",
                        "mail.smtp.trust", "*",
                        "mail.smtp.connectiontimeout", "5000",
                        "mail.smtp.timeout", "3000",
                        "mail.smtp.writetimeout", "5000"
                )
        );
        final MailConfigurations configuration = new MailConfigurations(mailProperties);

        // When
        final JavaMailSender result = configuration.javaMailSender();

        // Then
        assertNotNull(result);
        assertInstanceOf(JavaMailSenderImpl.class, result);
        JavaMailSenderImpl sender = (JavaMailSenderImpl) result;
        assertEquals("localhost", sender.getHost());
        assertEquals(1025, sender.getPort());
        assertEquals("admin", sender.getUsername());
        assertEquals("admin", sender.getPassword());

        final Properties javaMailProperties = sender.getJavaMailProperties();
        assertNotNull(javaMailProperties);
        assertEquals("true", javaMailProperties.getProperty("mail.smtp.auth"));
        assertEquals("true", javaMailProperties.getProperty("mail.smtp.starttls.enable"));
        assertEquals("*", javaMailProperties.getProperty("mail.smtp.trust"));
        assertEquals("5000", javaMailProperties.getProperty("mail.smtp.connectiontimeout"));
        assertEquals("3000", javaMailProperties.getProperty("mail.smtp.timeout"));
        assertEquals("5000", javaMailProperties.getProperty("mail.smtp.writetimeout"));
    }

    @Test
    @DisplayName("Should create JavaMailSender even when optional JavaMail properties are empty")
    void shouldCreateJavaMailSenderWithEmptyProperties() {
        // Given
        final MailProperties mailProperties = new MailProperties(
                "smtp.example.com",
                587,
                "user",
                "password",
                Map.of()
        );
        final MailConfigurations configuration = new MailConfigurations(mailProperties);

        // When
        final JavaMailSender result = configuration.javaMailSender();

        // Then
        assertNotNull(result);
        final JavaMailSenderImpl sender = (JavaMailSenderImpl) result;
        assertEquals("smtp.example.com", sender.getHost());
        assertEquals(587, sender.getPort());
        assertEquals("user", sender.getUsername());
        assertEquals("password", sender.getPassword());
        assertNotNull(sender.getJavaMailProperties());
        assertTrue(sender.getJavaMailProperties().isEmpty());
    }

    @Test
    @DisplayName("Should copy JavaMail properties defensively into a new Properties instance")
    void shouldNotReuseOriginalPropertiesMap() {
        // Given
        final Map<String, String> originalProperties =
                Map.of("mail.smtp.auth", "true");
        final MailProperties mailProperties = new MailProperties(
                "localhost",
                25,
                "admin",
                "admin",
                originalProperties
        );
        final MailConfigurations configuration = new MailConfigurations(mailProperties);

        // When
        final JavaMailSenderImpl sender =
                (JavaMailSenderImpl) configuration.javaMailSender();

        // Then
        final Properties javaMailProperties = sender.getJavaMailProperties();
        assertNotSame(originalProperties, javaMailProperties);
        assertEquals("true", javaMailProperties.getProperty("mail.smtp.auth"));
    }
}
