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

    private static final Class<?> CLASS = CustomerServiceImpl.class;
    private final ICustomerClient customerClient;

    @Override
    @Async("appTaskExecutor")
    public CompletableFuture<CustomerResponse> getCustomer(final String customerId) {
        log.info("Get Customer by ID: {}", customerId);
        try {
            final ApiResponse<CustomerResponse> customerResponse = customerClient.findCustomerById(customerId);
            if (null == customerResponse || null == customerResponse.data()) {
                return CompletableFuture.failedFuture(
                        new CustomerNotFoundExceptions(
                                "Customer data missing in response",
                                "getCustomer(String) in " + CLASS
                        )
                );
            }
            return CompletableFuture.completedFuture(customerResponse.data());
        } catch (feign.FeignException.NotFound ex) {
            log.warn(
                    "Customer not found while calling customer service. method=getCustomer, status={}, responseBody={}",
                    ex.status(),
                    ex.contentUTF8(),
                    ex
            );
            return CompletableFuture.failedFuture(
                    new CustomerNotFoundExceptions(
                            "No customer exists with the provided ID",
                            "getCustomer(String) in " + CLASS,
                            ex
                    )
            );
        } catch (feign.FeignException ex) {
            log.error(
                    "Customer service call failed. method=getCustomer, status={}, responseBody={}",
                    ex.status(),
                    ex.contentUTF8(),
                    ex
            );
            return CompletableFuture.failedFuture(
                    new CustomerNotFoundExceptions(
                            "Customer service error (status=" + ex.status() + ")",
                            "getCustomer(String) in " + CLASS,
                            ex
                    )
            );
        } catch (Exception ex) {
            log.error(
                    "Unexpected error while calling customer service. method=getCustomer",
                    ex
            );
            return CompletableFuture.failedFuture(
                    new CustomerNotFoundExceptions(
                            "Unexpected error while calling customer service",
                            "getCustomer(String) in " + CLASS,
                            ex
                    )
            );
        }
    }
}