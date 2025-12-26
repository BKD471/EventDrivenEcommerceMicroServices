package com.forsaken.ecommerce.order.configs.client_configurations.customer;


import com.forsaken.ecommerce.common.exceptions.BusinessException;
import com.forsaken.ecommerce.common.exceptions.CustomerNotFoundExceptions;
import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CustomerFeignErrorDecoder implements ErrorDecoder {

    private final ErrorDecoder defaultDecoder = new Default();

    @Override
    public Exception decode(final String methodKey, final Response response) {

        final int status = response.status();
        log.error(
                "Customer service call failed. methodKey={}, status={}",
                methodKey,
                status
        );

        return switch (status) {
            case 400 -> new BusinessException(
                    "Invalid customer request",
                    methodKey
            );
            case 404 -> new CustomerNotFoundExceptions(
                    "No customer exists with the provided ID",
                    methodKey
            );
            case 408, 504 -> new BusinessException(
                    "Customer service timeout",
                    methodKey
            );
            case 500, 502, 503 -> new BusinessException(
                    "Customer service unavailable",
                    methodKey
            );
            default -> defaultDecoder.decode(methodKey, response);
        };
    }
}
