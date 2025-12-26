package com.forsaken.ecommerce.order.configs.client_configurations.product;

import lombok.RequiredArgsConstructor;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.util.Timeout;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
@RequiredArgsConstructor
public class ProductRestConfiguration {

    private final ProductClientProperties productClientProperties;

    @Bean
    public RestTemplate productRestTemplate() {
        final RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(
                        Timeout.ofMilliseconds(
                                productClientProperties.connectTimeout().toMillis()
                        )
                )
                .setResponseTimeout(
                        Timeout.ofMilliseconds(
                                productClientProperties.readTimeout().toMillis()
                        )
                )
                .build();
        final CloseableHttpClient httpClient = HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .build();
        final HttpComponentsClientHttpRequestFactory factory =
                new HttpComponentsClientHttpRequestFactory(httpClient);
        return new RestTemplate(factory);
    }
}
