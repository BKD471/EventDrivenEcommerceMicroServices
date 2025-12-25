package com.forsaken.ecommerce.customer.controller;

import com.forsaken.ecommerce.common.exceptions.CustomerNotFoundExceptions;
import com.forsaken.ecommerce.common.responses.ApiResponse;
import com.forsaken.ecommerce.common.responses.PagedResponse;
import com.forsaken.ecommerce.customer.dto.CustomerRequest;
import com.forsaken.ecommerce.customer.dto.CustomerResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * REST controller contract for managing Customer resources.
 *
 * <p>This interface defines operations for creating, updating, retrieving,
 * validating, and deleting customers within the system. All responses are wrapped
 * inside a standardized {@link ApiResponse} format and returned via
 * {@link ResponseEntity} for proper HTTP semantics.
 *
 * <p>Validations such as `@NotBlank`, `@NotNull`, and `@Valid` ensure that incoming
 * request payloads and path variables meet structural requirements before reaching
 * the service layer.
 */
@RequestMapping("/api/v1/customers")
public interface ICustomerController {

    /**
     * Creates a new customer in the system.
     *
     * <p>This endpoint accepts a {@link CustomerRequest} payload, validates it,
     * and triggers the customer creation flow. If the customer already exists
     * or required linked entities are missing, a {@link CustomerNotFoundExceptions}
     * may be thrown.</p>
     *
     * @param request the customer creation request payload; must be valid
     * @return a ResponseEntity containing a standard {@link ApiResponse} wrapper
     *         with a success message and HTTP 201 status
     * @throws CustomerNotFoundExceptions if related entities required for customer creation are not found
     */
    @PostMapping("/create")
    ResponseEntity<ApiResponse<String>> createCustomer(
            @RequestBody @Valid final CustomerRequest request
    ) throws CustomerNotFoundExceptions;

    /**
     * Updates an existing customer in the system.
     *
     * <p>This endpoint receives a {@link CustomerRequest} payload containing
     * updated customer details. The request is validated before processing.
     * If the target customer does not exist, a {@link CustomerNotFoundExceptions}
     * is thrown.</p>
     *
     * @param request the updated customer information; must be valid
     * @return a ResponseEntity containing a standard {@link ApiResponse} wrapper
     *         with a success message and HTTP 200 status
     * @throws CustomerNotFoundExceptions if the customer to update is not found
     */
    @PutMapping
    ResponseEntity<ApiResponse<String>> updateCustomer(
            @RequestBody @Valid final CustomerRequest request
    ) throws CustomerNotFoundExceptions;

    /**
     * Retrieves a paginated list of customers.
     *
     * <p>This endpoint supports pagination using `page` and `size` parameters.
     * It returns customer data wrapped inside a {@link PagedResponse} structure
     * along with a standard {@link ApiResponse} wrapper.</p>
     *
     * <p>Page numbering starts from 1. If an invalid page or size value is provided,
     * the service layer may apply fallback defaults.</p>
     *
     * @param page the page number to retrieve; defaults to 1
     * @param size the number of records per page; defaults to 3
     * @return a ResponseEntity containing a paginated list of {@link CustomerResponse}
     *         inside a {@link PagedResponse}, wrapped in {@link ApiResponse}, with HTTP 200 status
     */
    @GetMapping
    ResponseEntity<ApiResponse<PagedResponse<CustomerResponse>>> findAll(
            @RequestParam(name = "page", defaultValue = "1") final int page,
            @RequestParam(name = "size", defaultValue = "3") final int size
    );

    /**
     * Checks whether a customer exists for the given customer ID.
     *
     * <p>This endpoint verifies the presence of a customer by its unique identifier.
     * It returns a boolean value wrapped in {@link ApiResponse} indicating whether
     * the customer exists in the system.</p>
     *
     * @param customerId the unique identifier of the customer; must not be blank
     * @return a ResponseEntity containing an {@link ApiResponse} with a boolean value
     *         (`true` if the customer exists, `false` otherwise) and an HTTP 200 status
     */
    @GetMapping("/exists/{customer-id}")
    ResponseEntity<ApiResponse<Boolean>> existsById(
            @PathVariable("customer-id") @NotBlank final String customerId
    );

    /**
     * Retrieves a customer's details by their unique identifier.
     *
     * <p>This endpoint fetches a single customer based on the provided customer ID.
     * If the customer does not exist, a {@link CustomerNotFoundExceptions} is thrown.</p>
     *
     * @param customerId the unique identifier of the customer; must not be blank
     * @return a ResponseEntity containing an {@link ApiResponse} that wraps a
     *         {@link CustomerResponse} object representing the customer's details
     * @throws CustomerNotFoundExceptions if no customer is found for the given ID
     */
    @GetMapping("/id/{customer-id}")
    ResponseEntity<ApiResponse<CustomerResponse>> findById(
            @PathVariable("customer-id") @NotBlank final String customerId
    ) throws CustomerNotFoundExceptions;

    /**
     * Retrieves customer details using their email address.
     *
     * <p>This endpoint searches for a customer based on the provided email.
     * If no customer is found, a {@link CustomerNotFoundExceptions} is thrown.</p>
     *
     * @param customerEmail the email address of the customer; must not be blank
     * @return a ResponseEntity containing an {@link ApiResponse} wrapping a
     *         {@link CustomerResponse} with the customer's details
     * @throws CustomerNotFoundExceptions if no customer exists for the given email
     */
    @GetMapping("/email/{customer-email}")
    ResponseEntity<ApiResponse<CustomerResponse>> findByEmail(
            @PathVariable("customer-email") @NotBlank final String customerEmail
    ) throws CustomerNotFoundExceptions;

    /**
     * Deletes a customer from the system based on the provided customer ID.
     *
     * <p>This endpoint removes the customer record permanently. If the customer
     * does not exist, the service layer is expected to handle and throw an
     * appropriate exception (e.g., CustomerNotFoundExceptions).</p>
     *
     * @param customerId the unique identifier of the customer to delete; must not be blank
     * @return a ResponseEntity containing an {@link ApiResponse} with a success message
     *         and HTTP 200 status once the customer is deleted
     */
    @DeleteMapping("/{customer-id}")
    ResponseEntity<ApiResponse<String>> delete(
            @PathVariable("customer-id") @NotBlank final String customerId
    );
}
