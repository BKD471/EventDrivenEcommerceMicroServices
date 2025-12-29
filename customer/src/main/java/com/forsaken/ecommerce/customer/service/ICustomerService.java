package com.forsaken.ecommerce.customer.service;

import com.forsaken.ecommerce.common.exceptions.CustomerNotFoundExceptions;
import com.forsaken.ecommerce.common.responses.PagedResponse;
import com.forsaken.ecommerce.customer.dto.CustomerRequest;
import com.forsaken.ecommerce.customer.dto.CustomerResponse;

/**
 * Service-layer contract for managing customer entities.
 *
 * <p>This interface defines the core business operations for creating,
 * updating, retrieving, validating, and deleting customers. All methods
 * enforce business rules and may trigger domain-specific exceptions such
 * as {@link CustomerNotFoundExceptions} when appropriate.</p>
 *
 * <p>The service layer acts as the boundary between controller-level request
 * handling and repository-level persistence operations, ensuring that all
 * customer-related workflows adhere to validation, consistency, and domain
 * rules.</p>
 */
public interface ICustomerService {

    /**
     * Creates a new customer based on the provided request data.
     *
     * <p>This method handles the business logic for creating a customer, including
     * validating the request, performing any necessary lookups, and persisting
     * the new customer record. If any related entity required for customer
     * creation (such as a reference or parent entity) is not found, a
     * {@link CustomerNotFoundExceptions} is thrown.</p>
     *
     * @param request the customer creation details; must not be null and should contain
     *                all required fields for customer creation
     * @return the unique identifier of the newly created customer
     * @throws CustomerNotFoundExceptions if a required entity referenced within the request is not found
     */
    String createCustomer(final CustomerRequest request) throws CustomerNotFoundExceptions;

    /**
     * Updates an existing customer using the information provided in the request.
     *
     * <p>This method performs the business logic required to update a customer's
     * record. The request must contain the customer's identifier along with the
     * fields to be updated. If the customer does not exist, or any referenced
     * entity required during the update cannot be found, a
     * {@link CustomerNotFoundExceptions} is thrown.</p>
     *
     * @param request the updated customer details; must not be null and should contain
     *                a valid customer identifier
     * @return the unique identifier of the updated customer
     * @throws CustomerNotFoundExceptions if the customer to update does not exist or a required
     *                                    referenced entity is not found
     */
    String updateCustomer(final CustomerRequest request) throws CustomerNotFoundExceptions;

    /**
     * Retrieves a paginated list of customers.
     *
     * <p>This method returns customer data in a {@link PagedResponse} structure,
     * allowing consumers to navigate through pages of results. Pagination is
     * controlled through the {@code page} and {@code size} parameters.</p>
     *
     * <p>Page numbering is expected to start from 1, unless the service layer applies
     * its own defaulting or normalization logic.</p>
     *
     * @param page the page number to retrieve; must be greater than zero
     * @param size the number of customer records to include per page; must be greater than zero
     * @return a {@link PagedResponse} containing a list of {@link CustomerResponse} objects
     *         along with pagination metadata such as total pages and total elements
     */
    PagedResponse<CustomerResponse> findAllCustomers(final Integer page, final Integer size);

    /**
     * Retrieves the details of a customer using their unique identifier.
     *
     * <p>This method performs the lookup for a customer and returns a
     * {@link CustomerResponse} containing the customer's information. If the
     * customer does not exist, a {@link CustomerNotFoundExceptions} is thrown.</p>
     *
     * @param customerId the unique identifier of the customer to retrieve; must not be null or blank
     * @return a {@link CustomerResponse} containing the customer's details
     * @throws CustomerNotFoundExceptions if no customer exists for the provided identifier
     */
    CustomerResponse findById(final String customerId) throws CustomerNotFoundExceptions;

    /**
     * Retrieves the details of a customer using their email address.
     *
     * <p>This method searches for a customer based on the provided email and
     * returns a {@link CustomerResponse} containing the customer's information.
     * If no customer exists with the given email, a
     * {@link CustomerNotFoundExceptions} is thrown.</p>
     *
     * @param customerEmail the email address of the customer to retrieve; must not be null or blank
     * @return a {@link CustomerResponse} containing the customer's details
     * @throws CustomerNotFoundExceptions if no customer is found for the provided email address
     */
    CustomerResponse findByEmail(final String customerEmail) throws CustomerNotFoundExceptions;

    /**
     * Checks whether a customer exists for the given customer ID.
     *
     * <p>This method performs an existence check without retrieving the full
     * customer record. It returns {@code true} if a customer with the specified
     * identifier exists, otherwise {@code false}.</p>
     *
     * @param customerId the unique identifier of the customer to check; must not be null or blank
     * @return {@code true} if the customer exists, otherwise {@code false}
     */
    boolean existsById(final String customerId);

    /**
     * Deletes a customer from the system using the provided customer ID.
     *
     * <p>This method performs the business logic for removing an existing customer.
     * If the customer does not exist, the implementation is expected to throw an
     * appropriate exception (e.g., {@link CustomerNotFoundExceptions}) or handle
     * the condition based on the service's error strategy.</p>
     *
     * @param customerId the unique identifier of the customer to delete; must not be null or blank
     * @return the unique identifier of the deleted customer
     */
    String deleteCustomer(final String customerId);
}
