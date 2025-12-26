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

    private final ProductClientProperties props;

    @Bean(destroyMethod = "close")
    public CloseableHttpClient productHttpClient() {
        final RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(Timeout.ofDays(Math.toIntExact(props.connectTimeout().toMillis())))
                .setResponseTimeout(Timeout.ofDays(Math.toIntExact(props.readTimeout().toMillis())))
                .build();
        return HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .build();
    }

    @Bean
    public RestTemplate productRestTemplate(final CloseableHttpClient productHttpClient) {
        final HttpComponentsClientHttpRequestFactory factory =
                new HttpComponentsClientHttpRequestFactory(productHttpClient);
        return new RestTemplate(factory);
    }
}
