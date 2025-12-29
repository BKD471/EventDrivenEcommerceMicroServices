package com.forsaken.ecommerce.customer.controller;


import com.forsaken.ecommerce.common.exceptions.CustomerNotFoundExceptions;
import com.forsaken.ecommerce.common.responses.ApiResponse;
import com.forsaken.ecommerce.common.responses.PagedResponse;
import com.forsaken.ecommerce.customer.dto.CustomerRequest;
import com.forsaken.ecommerce.customer.dto.CustomerResponse;
import com.forsaken.ecommerce.customer.service.ICustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;


@RestController
@RequiredArgsConstructor
public class CustomerControllerImpl implements ICustomerController {

    private final ICustomerService customerService;

    @Override
    public ResponseEntity<ApiResponse<String>> createCustomer(final CustomerRequest request) throws CustomerNotFoundExceptions {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                        ApiResponse.<String>builder()
                                .status(ApiResponse.Status.SUCCESS)
                                .data(customerService.createCustomer(request))
                                .message("Customer Created")
                                .build()
                );
    }

    @Override
    public ResponseEntity<ApiResponse<String>> updateCustomer(final CustomerRequest request) throws CustomerNotFoundExceptions {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(
                        ApiResponse.<String>builder()
                                .status(ApiResponse.Status.SUCCESS)
                                .data(customerService.updateCustomer(request))
                                .message("Customer Updated")
                                .build()
                );
    }

    @Override
    public ResponseEntity<ApiResponse<PagedResponse<CustomerResponse>>> findAll(
            final Integer page,
            final String cursorCustomerId
    ) {
        final Map<String, String> cursor =
                cursorCustomerId != null
                        ? Map.of("customerId", cursorCustomerId)
                        : null;
        return ResponseEntity.status(HttpStatus.OK)
                .body(
                        ApiResponse.<PagedResponse<CustomerResponse>>builder()
                                .status(ApiResponse.Status.SUCCESS)
                                .data(customerService.findAllCustomers(page, cursor))
                                .message("All Customers Data Fetched")
                                .build()
                );
    }

    @Override
    public ResponseEntity<ApiResponse<Boolean>> existsById(final String customerId) {
        final boolean exists = customerService.existsById(customerId);
        final String message = exists ? "Customer " + customerId + " exists" : "Customer " + customerId + " not found";
        final ApiResponse.Status status = exists ? ApiResponse.Status.SUCCESS : ApiResponse.Status.FAILED;

        return ResponseEntity.status(HttpStatus.OK)
                .body(
                        ApiResponse.<Boolean>builder()
                                .status(status)
                                .data(exists)
                                .message(message)
                                .build()
                );
    }

    @Override
    public ResponseEntity<ApiResponse<CustomerResponse>> findById(final String customerId) throws CustomerNotFoundExceptions {
        return ResponseEntity.status(HttpStatus.OK)
                .body(
                        ApiResponse.<CustomerResponse>builder()
                                .status(ApiResponse.Status.SUCCESS)
                                .data(customerService.findById(customerId))
                                .message("Customer data fetched for the id " + customerId)
                                .build()
                );
    }

    @Override
    public ResponseEntity<ApiResponse<CustomerResponse>> findByEmail(final String customerEmail) throws CustomerNotFoundExceptions {
        return ResponseEntity.status(HttpStatus.OK)
                .body(
                        ApiResponse.<CustomerResponse>builder()
                                .status(ApiResponse.Status.SUCCESS)
                                .data(customerService.findByEmail(customerEmail))
                                .message("Customer data fetched for the emailId " + customerEmail)
                                .build()
                );
    }

    @Override
    public ResponseEntity<ApiResponse<String>> delete(final String customerId) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(
                        ApiResponse.<String>builder()
                                .status(ApiResponse.Status.SUCCESS)
                                .data(customerService.deleteCustomer(customerId))
                                .message("Customer Deleted for the id " + customerId)
                                .build()
                );
    }
}
