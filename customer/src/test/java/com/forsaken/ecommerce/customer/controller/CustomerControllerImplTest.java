package com.forsaken.ecommerce.customer.controller;


import com.forsaken.ecommerce.common.exceptions.CustomerNotFoundExceptions;
import com.forsaken.ecommerce.common.responses.ApiResponse;
import com.forsaken.ecommerce.common.responses.PagedResponse;
import com.forsaken.ecommerce.customer.dto.CustomerRequest;
import com.forsaken.ecommerce.customer.dto.CustomerResponse;
import com.forsaken.ecommerce.customer.model.Address;
import com.forsaken.ecommerce.customer.service.ICustomerService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CustomerControllerImpl}, validating that the controller
 * correctly delegates to {@link ICustomerService}, constructs proper
 * {@link ApiResponse} objects, and returns the expected {@link ResponseEntity}
 * status codes.
 *
 * <p>This test class uses Mockito for mocking dependencies and focuses on
 * controller-layer behavior only—no service or persistence logic is executed.
 *
 * <p>Test scenarios include:
 * <ul>
 *     <li>Customer creation</li>
 *     <li>Customer update</li>
 *     <li>Pagination retrieval of customers</li>
 *     <li>Existence checks</li>
 *     <li>Fetch by ID</li>
 *     <li>Fetch by email</li>
 *     <li>Customer deletion</li>
 *     <li>Exception propagation for not-found cases</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class CustomerControllerImplTest {

    private static final String CUSTOMER_ID = "cust-123";
    private static final String CUSTOMER_EMAIL = "abc@gmail.com";

    @Mock
    private ICustomerService customerService;

    @InjectMocks
    private CustomerControllerImpl controller;


    /**
     * Tests that the controller correctly handles a create-customer request
     * by:
     * <ul>
     *     <li>Delegating to {@link ICustomerService#createCustomer}</li>
     *     <li>Returning HTTP 201 (CREATED)</li>
     *     <li>Wrapping the service response inside {@link ApiResponse}</li>
     * </ul>
     */
    @Test
    void createCustomer_ReturnsCreatedResponse() throws CustomerNotFoundExceptions {
        // Given
        final CustomerRequest request = constructCustomerRequest();
        final String serviceResponse = "created-customer-object";

        when(customerService.createCustomer(request)).thenReturn(serviceResponse);

        // When
        final ResponseEntity<ApiResponse<String>> resp = controller.createCustomer(request);

        // Then
        assertEquals(HttpStatus.CREATED, resp.getStatusCode());
        assertNotNull(resp.getBody());
        final ApiResponse<?> body = resp.getBody();
        assertEquals(ApiResponse.Status.SUCCESS, body.status());
        assertEquals("Customer Created", body.message());
        assertEquals(serviceResponse, body.data());
        verify(customerService, times(1)).createCustomer(request);
    }

    /**
     * Tests that updating an existing customer results in:
     * <ul>
     *     <li>Delegation to the service layer</li>
     *     <li>HTTP 202 (ACCEPTED)</li>
     *     <li>Correct success message and returned data</li>
     * </ul>
     */
    @Test
    void updateCustomer_ReturnsAcceptedResponse() throws CustomerNotFoundExceptions {
        // Given
        final CustomerRequest request = constructCustomerRequest();
        final String serviceResponse = "updated-customer-object";
        when(customerService.updateCustomer(request)).thenReturn(serviceResponse);

        // When
        final ResponseEntity<ApiResponse<String>> resp = controller.updateCustomer(request);

        // Then
        assertEquals(HttpStatus.ACCEPTED, resp.getStatusCode());
        final ApiResponse<?> body = resp.getBody();
        assertNotNull(body);
        assertEquals(ApiResponse.Status.SUCCESS, body.status());
        assertEquals("Customer Updated", body.message());
        assertEquals(serviceResponse, body.data());
        verify(customerService, times(1)).updateCustomer(request);
    }

    /**
     * Tests that retrieving all customers with pagination:
     * <ul>
     *     <li>Invokes the service with correct paging parameters</li>
     *     <li>Returns HTTP 200 (OK)</li>
     *     <li>Wraps the result inside {@link ApiResponse} with SUCCESS status</li>
     * </ul>
     */
    @Test
    void findAll_WithPagination_ReturnsPagedResponse() {
        // Given
        final Integer pageSize = 2;
        final CustomerResponse customer1 = constructCustomerResponse();
        final CustomerResponse customer2 = constructCustomerResponse();
        final PagedResponse<CustomerResponse> pagedResponse =
                PagedResponse.<CustomerResponse>builder()
                        .content(List.of(customer1, customer2))
                        .size(2)
                        .nextCursor(Map.of("customerId", "CUST#123"))
                        .build();
        when(customerService.findAllCustomers(pageSize, null))
                .thenReturn(pagedResponse);

        // When
        final ResponseEntity<ApiResponse<PagedResponse<CustomerResponse>>> response =
                controller.findAll(pageSize, null);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());

        final ApiResponse<PagedResponse<CustomerResponse>> body = response.getBody();
        assertNotNull(body);

        assertEquals(ApiResponse.Status.SUCCESS, body.status());
        assertEquals("All Customers Data Fetched", body.message());
        assertEquals(pagedResponse, body.data());
        verify(customerService).findAllCustomers(pageSize, null);
    }

    /**
     * Tests the existence check when the customer exists:
     * <ul>
     *     <li>Service returns true</li>
     *     <li>Controller returns SUCCESS status</li>
     *     <li>Correct success message is included</li>
     * </ul>
     */
    @Test
    void existsById_WhenExists_ReturnsSuccessStatus() {
        // Given
        when(customerService.existsById(CUSTOMER_ID)).thenReturn(true);

        // When
        final ResponseEntity<ApiResponse<Boolean>> resp = controller.existsById(CUSTOMER_ID);

        // Then
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        final ApiResponse<?> body = resp.getBody();
        assertNotNull(body);
        assertEquals(ApiResponse.Status.SUCCESS, body.status());
        assertEquals(true, body.data());
        assertEquals("Customer " + CUSTOMER_ID + " exists", body.message());
        verify(customerService, times(1)).existsById(CUSTOMER_ID);
    }

    /**
     * Tests the existence check when the customer does NOT exist:
     * <ul>
     *     <li>Service returns false</li>
     *     <li>Controller returns FAILED status</li>
     *     <li>Correct not-found message is returned</li>
     * </ul>
     */
    @Test
    void existsById_WhenNotExists_ReturnsFailedStatus() {
        // Given
        when(customerService.existsById(CUSTOMER_ID)).thenReturn(false);

        // When
        final ResponseEntity<ApiResponse<Boolean>> resp = controller.existsById(CUSTOMER_ID);

        // Then
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        final ApiResponse<?> body = resp.getBody();
        assertNotNull(body);
        assertEquals(ApiResponse.Status.FAILED, body.status());
        assertEquals(false, body.data());
        assertEquals("Customer " + CUSTOMER_ID + " not found", body.message());
        verify(customerService, times(1)).existsById(CUSTOMER_ID);
    }

    /**
     * Tests fetching a customer by ID when the record exists:
     * <ul>
     *     <li>Service returns a populated {@link CustomerResponse}</li>
     *     <li>Controller wraps it inside a SUCCESS {@link ApiResponse}</li>
     *     <li>Returns HTTP 200 (OK)</li>
     * </ul>
     */
    @Test
    void findById_ReturnsCustomer_WhenFound() throws CustomerNotFoundExceptions {
        // Given
        final CustomerResponse serviceResult = constructCustomerResponse();
        when(customerService.findById(CUSTOMER_ID)).thenReturn(serviceResult);

        // When
        final ResponseEntity<ApiResponse<CustomerResponse>> resp = controller.findById(CUSTOMER_ID);

        // Then
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        final ApiResponse<?> body = resp.getBody();
        assertNotNull(body);
        assertEquals(ApiResponse.Status.SUCCESS, body.status());
        assertEquals("Customer data fetched for the id " + CUSTOMER_ID, body.message());
        assertEquals(serviceResult, body.data());
        verify(customerService, times(1)).findById(CUSTOMER_ID);
    }

    /**
     * Ensures that the controller does NOT swallow {@link CustomerNotFoundExceptions}.
     * <p>Since the controller declares `throws CustomerNotFoundExceptions`,
     * the exception should be propagated outward.</p>
     */
    @Test
    void findById_PropagatesException_WhenNotFound() throws CustomerNotFoundExceptions {
        // Given
        when(customerService.findById(CUSTOMER_ID)).thenThrow(new CustomerNotFoundExceptions("not found", ""));

        // When / Then - controller method declares throws, so it should propagate
        assertThrows(CustomerNotFoundExceptions.class, () -> controller.findById(CUSTOMER_ID));
        verify(customerService, times(1)).findById(CUSTOMER_ID);
    }

    /**
     * Tests fetching customer by email:
     * <ul>
     *     <li>Service returns a matching {@link CustomerResponse}</li>
     *     <li>Controller responds with HTTP 200 (OK)</li>
     *     <li>Correct success message is included</li>
     * </ul>
     */
    @Test
    void findByEmail_ReturnsCustomer_WhenFound() throws CustomerNotFoundExceptions {
        // Given
        final CustomerResponse serviceResult = constructCustomerResponse();
        when(customerService.findByEmail(CUSTOMER_EMAIL)).thenReturn(serviceResult);

        // When
        final ResponseEntity<ApiResponse<CustomerResponse>> resp = controller.findByEmail(CUSTOMER_EMAIL);

        // Then
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        final ApiResponse<?> body = resp.getBody();
        assertNotNull(body);
        assertEquals(ApiResponse.Status.SUCCESS, body.status());
        assertEquals("Customer data fetched for the emailId " + CUSTOMER_EMAIL, body.message());
        assertEquals(serviceResult, body.data());
        verify(customerService, times(1)).findByEmail(CUSTOMER_EMAIL);
    }

    /**
     * Tests customer deletion logic:
     * <ul>
     *     <li>Service returns confirmation string</li>
     *     <li>Controller returns SUCCESS ApiResponse</li>
     *     <li>Includes correct deletion message</li>
     *     <li>Returns HTTP 200 (OK)</li>
     * </ul>
     */
    @Test
    void delete_ReturnsSuccess() {
        // Given
        final String deleteResult = "deleted";
        when(customerService.deleteCustomer(CUSTOMER_ID)).thenReturn(deleteResult);

        // When
        final ResponseEntity<ApiResponse<String>> resp = controller.delete(CUSTOMER_ID);

        // Then
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        final ApiResponse<?> body = resp.getBody();
        assertNotNull(body);
        assertEquals(ApiResponse.Status.SUCCESS, body.status());
        assertEquals(deleteResult, body.data());
        assertEquals("Customer Deleted for the id " + CUSTOMER_ID, body.message());
        verify(customerService, times(1)).deleteCustomer(CUSTOMER_ID);
    }

    /**
     * Helper method for constructing a sample {@link CustomerRequest} used in tests.
     */
    private CustomerRequest constructCustomerRequest() {
        return CustomerRequest.builder()
                .id(CUSTOMER_ID)
                .firstname("test-user-firstname-123")
                .lastname("test-user-lastname-123")
                .email(CUSTOMER_EMAIL)
                .address(constructAddress())
                .build();
    }

    /**
     * Helper method for constructing a sample {@link Address}.
     */
    private Address constructAddress() {
        return Address.builder()
                .street("Street-123")
                .houseNumber("houseNumber-123")
                .zipCode("zipCOde-123")
                .build();
    }

    /**
     * Helper method for constructing a sample {@link CustomerResponse}.
     */
    private CustomerResponse constructCustomerResponse() {
        return CustomerResponse.builder()
                .id(CUSTOMER_ID)
                .firstname("test-user-firstname-123")
                .lastname("test-user-lastname-123")
                .email(CUSTOMER_EMAIL)
                .address(constructAddress())
                .build();
    }
}