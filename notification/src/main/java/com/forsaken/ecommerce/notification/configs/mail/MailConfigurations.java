package com.forsaken.ecommerce.notification.configs.mail;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

@Configuration
@RequiredArgsConstructor
public class MailConfigurations {

    private final MailProperties mailProperties;

    @Bean
    public JavaMailSender javaMailSender() {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();

        sender.setHost(mailProperties.host());
        sender.setPort(mailProperties.port());
        sender.setUsername(mailProperties.username());
        sender.setPassword(mailProperties.password());

        final Properties properties = new Properties();
        properties.putAll(mailProperties.properties());
        sender.setJavaMailProperties(properties);
        return sender;
    }
}
