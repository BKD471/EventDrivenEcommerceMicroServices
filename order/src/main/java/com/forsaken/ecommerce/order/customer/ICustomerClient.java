package com.forsaken.ecommerce.order.customer;

import com.forsaken.ecommerce.common.responses.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Optional;

/**
 * Feign client for communicating with the Customer Service.
 * <p>
 * This client is responsible for retrieving customer information
 * from the external customer-service API using RESTful endpoints.
 * The base URL for the service is configured through the property
 * {@code application.config.customer-url}.
 * </p>
 *
 * <p><b>Usage:</b></p>
 * <pre>
 * Optional<CustomerResponse> customer = customerClient.findCustomerById("123");
 * customer.ifPresent(...);
 * </pre>
 *
 * <p><b>Error Handling:</b></p>
 * <ul>
 *     <li>If the customer is not found (e.g., 404), the method returns an empty {@link Optional}.</li>
 *     <li>If the remote service is unreachable or responds with an error status,
 *     Feign will throw an appropriate exception (e.g., FeignException).</li>
 * </ul>
 *
 * @author Your Name
 * @see CustomerResponse
 */
@FeignClient(
        name = "customer-service",
        url = "${application.config.customer-url}"
)
public interface ICustomerClient {

    /**
     * Retrieves customer details by their unique ID.
     *
     * <p>This method sends a GET request to the Customer Service endpoint
     * {@code /{customer-id}}. If the customer exists, the service returns
     * a {@link CustomerResponse}; otherwise, an empty {@link Optional}
     * is returned.</p>
     *
     * @param customerId the unique identifier of the customer to lookup
     * @return an {@link Optional} containing the customer details if found,
     * otherwise an empty Optional
     */
    @GetMapping("/id/{customer-id}")
    ApiResponse<CustomerResponse> findCustomerById(@PathVariable("customer-id") String customerId);
}
