package com.forsaken.ecommerce.order.configs.rest;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
@RequiredArgsConstructor
public class RestTemplateConfig {

    private final RestClientProperties restClientProperties;

    @Bean
    public RestTemplate restTemplate(final RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(restClientProperties.connectTimeout())
                .setReadTimeout(restClientProperties.readTimeout())
                .build();
    }
}
