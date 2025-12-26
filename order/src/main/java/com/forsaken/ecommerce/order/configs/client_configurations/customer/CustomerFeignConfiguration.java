package com.forsaken.ecommerce.order.configs.client_configurations.customer;

import com.forsaken.ecommerce.order.customer.ICustomerClient;
import feign.Feign;
import feign.Request;
import feign.Target;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class CustomerFeignConfiguration {

    private final CustomerClientProperties props;

    @Bean
    public Feign.Builder feignBuilder() {
        return Feign.builder()
                .options(new Request.Options(
                        Math.toIntExact(props.connectTimeout().toMillis()),
                        Math.toIntExact(props.readTimeout().toMillis())
                ));
    }

    @Bean
    public Target<?> paymentTarget() {
        return new Target.HardCodedTarget<>(
                ICustomerClient.class,
                props.url()
        );
    }
}
