package com.forsaken.ecommerce.order.configs.client_configurations.payment;

import feign.Request;
import feign.codec.ErrorDecoder;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
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
}
