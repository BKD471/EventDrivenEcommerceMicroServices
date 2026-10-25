package com.forsaken.ecommerce.product.configs.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.util.Assert;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.DefaultCsrfToken;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Configuration
public class ServiceSecurityConfiguration {

    @Bean
    public SecurityFilterChain productSecurityFilterChain(
            final HttpSecurity http,
            final CsrfTokenRepository csrfTokenRepository
    ) throws Exception {
        return http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        .requireCsrfProtectionMatcher(csrfProtectionMatcher())
                )
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.POST, "/api/v1/products/reservations/*/release")
                        .hasRole("ORDER_SERVICE")
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/products/reservations/*/reconciliation",
                                "/api/v1/products/reservations/reconciliation")
                        .hasRole("ORDER_SERVICE")
                        .anyRequest().permitAll()
                )
                .httpBasic(Customizer.withDefaults())
                .build();
    }

    @Bean
    public CsrfTokenRepository serviceCsrfTokenRepository(
            @Value("${ORDER_SERVICE_PASSWORD}") final String password
    ) {
        Assert.hasText(password, "ORDER_SERVICE_PASSWORD must be configured");
        return new CsrfTokenRepository() {
            @Override
            public CsrfToken generateToken(final HttpServletRequest request) {
                return new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", password);
            }

            @Override
            public void saveToken(
                    final CsrfToken token,
                    final HttpServletRequest request,
                    final HttpServletResponse response
            ) {
            }

            @Override
            public CsrfToken loadToken(final HttpServletRequest request) {
                return generateToken(request);
            }
        };
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

    private static RequestMatcher csrfProtectionMatcher() {
        final RequestMatcher reservationRelease = new AntPathRequestMatcher(
                "/api/v1/products/reservations/*/release",
                HttpMethod.POST.name()
        );
        return request -> reservationRelease.matches(request)
                && request.getHeader(HttpHeaders.AUTHORIZATION) != null
                && request.getHeader(HttpHeaders.AUTHORIZATION).startsWith("Basic ");
    }
}
