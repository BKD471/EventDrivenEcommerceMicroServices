package com.forsaken.ecommerce.payment.configs.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.Assert;

@Configuration
public class ServiceSecurityConfiguration {

    @Bean
    public SecurityFilterChain paymentSecurityFilterChain(final HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/payments/*/confirm",
                                "/api/v1/payments/*/cancel"
                        ).hasRole("ORDER_SERVICE")
                        .anyRequest().permitAll()
                )
                .httpBasic(Customizer.withDefaults())
                .build();
    }

    @Bean
    public UserDetailsService orderServiceUserDetailsService(
            @Value("${ORDER_SERVICE_PASSWORD}") final String password,
            final PasswordEncoder passwordEncoder
    ) {
        Assert.hasText(password, "ORDER_SERVICE_PASSWORD must be configured");
        return new InMemoryUserDetailsManager(User.withUsername("order-service")
                .password(passwordEncoder.encode(password))
                .roles("ORDER_SERVICE")
                .build());
    }

    @Bean
    public PasswordEncoder servicePasswordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
