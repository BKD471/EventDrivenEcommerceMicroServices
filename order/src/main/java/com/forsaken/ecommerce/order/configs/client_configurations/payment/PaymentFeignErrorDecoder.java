package com.forsaken.ecommerce.order.configs.client_configurations.payment;


import com.forsaken.ecommerce.common.exceptions.BusinessException;
import com.forsaken.ecommerce.common.exceptions.PaymentFailedExceptions;
import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PaymentFeignErrorDecoder implements ErrorDecoder {

    private final ErrorDecoder defaultDecoder = new Default();

    @Override
    public Exception decode(String methodKey, Response response) {

        final int status = response.status();
        log.error(
                "Payment service call failed. methodKey={}, status={}",
                methodKey,
                status
        );

        return switch (status) {
            case 400 -> new BusinessException(
                    "Invalid payment request",
                    methodKey
            );

            case 402 -> new PaymentFailedExceptions(
                    "Payment was declined by payment provider",
                    methodKey
            );

            case 404 -> new PaymentFailedExceptions(
                    "Payment service endpoint not found",
                    methodKey
            );

            case 408, 504 -> new PaymentFailedExceptions(
                    "Payment service timeout",
                    methodKey
            );

            case 500, 502, 503 -> new PaymentFailedExceptions(
                    "Payment service unavailable",
                    methodKey
            );
            default -> defaultDecoder.decode(methodKey, response);
        };
    }
}