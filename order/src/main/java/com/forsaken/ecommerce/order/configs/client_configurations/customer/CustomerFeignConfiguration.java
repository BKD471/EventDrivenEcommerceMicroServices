package com.forsaken.ecommerce.order.configs.client_configurations.customer;

import com.forsaken.ecommerce.order.customer.ICustomerClient;
import feign.Feign;
import feign.Request;
import feign.Target;
import feign.codec.ErrorDecoder;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class CustomerFeignConfiguration {

    private final CustomerClientProperties props;

    @Bean
    public Request.Options customerRequestOptions() {
        return new Request.Options(
                Math.toIntExact(props.connectTimeout().toMillis()),
                Math.toIntExact(props.readTimeout().toMillis())
        );
    }

    /**
     * Maps HTTP errors from Customer service
     * to domain-specific exceptions.
     */
    @Bean
    public ErrorDecoder customerErrorDecoder() {
        return new CustomerFeignErrorDecoder();
    }
}
