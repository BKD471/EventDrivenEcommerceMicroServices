package com.forsaken.ecommerce.order.customer;


import com.forsaken.ecommerce.common.exceptions.CustomerNotFoundExceptions;
import com.forsaken.ecommerce.common.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;


@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerServiceImpl implements ICustomerService {

    private final ICustomerClient customerClient;
    private final Class<?> className = CustomerServiceImpl.class;

    @Override
    @Async("appTaskExecutor")
    public CompletableFuture<CustomerResponse> getCustomer(final String customerId) throws CustomerNotFoundExceptions {
        log.info("Get Customer by ID: {}", customerId);
        try {
            final ApiResponse<CustomerResponse> customerResponse = customerClient.findCustomerById(customerId);
            if (null == customerResponse || null == customerResponse.data()) {
                throw new CustomerNotFoundExceptions(
                        "Cannot create order:: Customer data is missing in the response",
                        "getCustomer(final String customerId) in " + className
                );
            }
            return CompletableFuture.completedFuture(customerResponse.data());
        } catch (feign.FeignException.NotFound ex) {
            throw new CustomerNotFoundExceptions(
                    "Cannot create order:: No customer exists with the provided ID",
                    "getCustomer(final String customerId) in " + className,
                    ex
            );

        } catch (feign.FeignException ex) {
            throw new CustomerNotFoundExceptions(
                    "Cannot create order:: Customer service error (status=" + ex.status() + ")",
                    "getCustomer(final String customerId) in " + className,
                    ex
            );
        }
    }
}