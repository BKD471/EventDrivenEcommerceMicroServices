package com.forsaken.ecommerce.order.configs.client_configurations.payment;

import feign.Request;
import feign.RequestInterceptor;
import feign.auth.BasicAuthRequestInterceptor;
import feign.codec.ErrorDecoder;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

@RequiredArgsConstructor
public class PaymentFeignConfiguration {

    private final PaymentClientProperties props;

    @Bean
    public Request.Options paymentRequestOptions() {
        return new Request.Options(
                Math.toIntExact(props.connectTimeout().toMillis()),
                Math.toIntExact(props.readTimeout().toMillis())
        );
    }

    /**
     * Optional: Custom error decoder for mapping HTTP errors
     * to domain-specific exceptions.
     */
    @Bean
    public ErrorDecoder paymentErrorDecoder() {
        return new PaymentFeignErrorDecoder();
    }

    @Bean
    public RequestInterceptor paymentServiceAuthentication(
            @Value("${ORDER_SERVICE_PASSWORD}") final String password
    ) {
        return new BasicAuthRequestInterceptor("order-service", password);
    }
}
