package com.forsaken.ecommerce.order.configs.client_configurations.payment;

import com.forsaken.ecommerce.order.payment.IPaymentClient;
import feign.Feign;
import feign.Request;
import feign.Target;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class PaymentFeignConfiguration {

    private final PaymentClientProperties props;

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
                IPaymentClient.class,
                props.url()
        );
    }
}
