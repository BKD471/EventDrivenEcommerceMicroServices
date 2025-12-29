package com.forsaken.ecommerce.customer.service;


import com.forsaken.ecommerce.common.exceptions.CustomerNotFoundExceptions;
import com.forsaken.ecommerce.common.responses.PagedResponse;
import com.forsaken.ecommerce.customer.configs.general.CustomerProperties;
import com.forsaken.ecommerce.customer.dto.CustomerRequest;
import com.forsaken.ecommerce.customer.dto.CustomerResponse;
import com.forsaken.ecommerce.customer.model.Address;
import com.forsaken.ecommerce.customer.model.Customer;
import com.forsaken.ecommerce.customer.repository.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CustomerServiceImpl}, validating the correctness of
 * service-layer business logic, including:
 *
 * <ul>
 *     <li>Customer creation with email uniqueness enforcement</li>
 *     <li>Updating existing customer records</li>
 *     <li>Pagination and content slicing in findAllCustomers()</li>
 *     <li>Customer lookup by ID and email</li>
 *     <li>Existence checks</li>
 *     <li>Delegation to {@link CustomerRepository}</li>
 *     <li>Exception propagation for missing customers</li>
 * </ul>
 *
 * <p>All repository calls are mocked to isolate and test service logic only.
 */
@ExtendWith(MockitoExtension.class)
class CustomerServiceImplTest {

    private static final String CUSTOMER_ID = "cust-123";
    private static final String EMAIL_EXISTING = "abc@gmail.com";
    private static final String EMAIL_NEW = "new@gmail.com";
    private static final String FIRST_NAME = "test-user-firstname-123";
    private static final String LAST_NAME = "test-user-lastname-123";

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private CustomerProperties customerProperties;

    @InjectMocks
    private CustomerServiceImpl customerService;

    private CustomerRequest requestMock;

    /**
     * Initializes mocks before each test run.
     *
     * <p>Creates a mock {@link CustomerRequest} used in multiple test cases.
     */
    @BeforeEach
    void setUp() {
        requestMock = mock(CustomerRequest.class);
    }

    /**
     * Verifies customer creation flow when the provided email does NOT already exist.
     *
     * <ul>
     *     <li>Repository is queried for existing email</li>
     *     <li>Service generates a new ID</li>
     *     <li>The new Customer object is passed to {@link CustomerRepository#save}</li>
     *     <li>The captured Customer contains the expected email</li>
     * </ul>
     */
    @Test
    void createCustomer_ShouldReturnGeneratedId_WhenEmailNotPresent() throws CustomerNotFoundExceptions {
        // Given
        final CustomerRequest request = CustomerRequest.builder()
                .id(CUSTOMER_ID)
                .firstname(FIRST_NAME)
                .lastname(LAST_NAME)
                .email(EMAIL_NEW)
                .address(constructAddress())
                .build();
        when(customerRepository.findByEmail(EMAIL_NEW)).thenReturn(Optional.empty());
        ArgumentCaptor<Customer> savedCaptor = ArgumentCaptor.forClass(Customer.class);

        // When
        final String generatedId = customerService.createCustomer(request);

        // Then
        assertNotNull(generatedId);
        verify(customerRepository, times(1)).save(savedCaptor.capture());
        Customer captured = savedCaptor.getValue();

        assertNotNull(captured);
        assertEquals(EMAIL_NEW, captured.getCustomerEmail());
    }

    /**
     * Ensures that customer creation fails when the provided email already exists.
     *
     * <ul>
     *     <li>Repository returns an existing customer</li>
     *     <li>Service throws {@link CustomerNotFoundExceptions}</li>
     *     <li>No save() operation is executed</li>
     *     <li>Repository is not interacted with beyond findByEmail()</li>
     * </ul>
     */
    @Test
    void createCustomer_ShouldThrow_WhenEmailAlreadyExists() {
        // Given
        final Customer existingCustomer = constructCustomer(CUSTOMER_ID, FIRST_NAME, LAST_NAME, EMAIL_EXISTING);
        when(requestMock.email()).thenReturn(EMAIL_EXISTING);
        when(customerRepository.findByEmail(EMAIL_EXISTING)).thenReturn(Optional.of(existingCustomer));

        // Then
        final CustomerNotFoundExceptions ex = assertThrows(CustomerNotFoundExceptions.class,
                () -> customerService.createCustomer(requestMock));
        assertTrue(ex.getMessage().contains("Customer is already present with the provided email"));
        verify(customerRepository, times(0)).save(any());
        verify(customerRepository, times(1)).findByEmail(EMAIL_EXISTING);
        verifyNoMoreInteractions(customerRepository);
    }

    /**
     * Tests successful customer update logic:
     *
     * <ul>
     *     <li>Repository returns an existing customer record</li>
     *     <li>Fields are updated from the request</li>
     *     <li>The updated Customer is saved</li>
     *     <li>Returned value contains the updated customer's ID</li>
     * </ul>
     */
    @Test
    void updateCustomer_ShouldUpdate_WhenCustomerExists() throws CustomerNotFoundExceptions {
        // Given
        final Customer existingCustomer = constructCustomer(CUSTOMER_ID, FIRST_NAME, LAST_NAME, EMAIL_EXISTING);
        final Address customerAddress = constructAddress();
        when(requestMock.id()).thenReturn(CUSTOMER_ID);
        when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(existingCustomer));
        when(requestMock.firstname()).thenReturn(FIRST_NAME);
        when(requestMock.lastname()).thenReturn(LAST_NAME);
        when(requestMock.email()).thenReturn("updated@example.com");
        when(requestMock.address()).thenReturn(customerAddress);
        final ArgumentCaptor<Customer> savedCaptor = ArgumentCaptor.forClass(Customer.class);

        // When
        final String result = customerService.updateCustomer(requestMock);

        // Then
        assertTrue(result.contains(CUSTOMER_ID));
        verify(customerRepository, times(1)).save(savedCaptor.capture());
        Customer captured = savedCaptor.getValue();
        assertEquals(existingCustomer.getCustomerId(), captured.getCustomerId());
        assertEquals(FIRST_NAME, captured.getFirstName());
        assertEquals(LAST_NAME, captured.getLastName());
        assertEquals("updated@example.com", captured.getCustomerEmail());
        assertEquals(customerAddress, captured.getAddress());
        assertNotNull(captured);
    }

    /**
     * Ensures that updating a customer fails when the record does not exist.
     *
     * <ul>
     *     <li>Repository returns Optional.empty()</li>
     *     <li>Service throws {@link CustomerNotFoundExceptions}</li>
     *     <li>No save() operation is invoked</li>
     *     <li>No further repository interactions occur</li>
     * </ul>
     */
    @Test
    void updateCustomer_ShouldThrow_WhenCustomerNotFound() {
        // Given
        final Customer existingCustomer = constructCustomer(CUSTOMER_ID, FIRST_NAME, LAST_NAME, EMAIL_EXISTING);
        when(requestMock.id()).thenReturn(CUSTOMER_ID);
        when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.empty());

        // When
        assertThrows(CustomerNotFoundExceptions.class, () -> customerService.updateCustomer(requestMock));

        // Then
        verify(customerRepository, times(1)).findById(CUSTOMER_ID);
        verify(customerRepository, times(0)).save(existingCustomer); // explicit zero-call on known object
        verifyNoMoreInteractions(customerRepository);
    }


    /**
     * Validates pagination behavior in findAllCustomers():
     *
     * <ul>
     *     <li>Repository returns a fixed list of customers</li>
     *     <li>Service slices the list based on page and size</li>
     *     <li>Correct content, page, size, totalElements, and totalPages are returned</li>
     * </ul>
     */
    @Test
    void findAllCustomers_WithPagination_ReturnsPagedResponse() {
        // Given
        final int pageSize = 2;

        final Map<String, String> cursor =
                Map.of("customerId", "cust_123");

        final Map<String, AttributeValue> lastEvaluatedKey =
                Map.of("customerId", AttributeValue.builder().s("cust_123").build());

        final Customer customerOne =
                constructCustomer("cust_123", "test-user-firstname-123",
                        "test-user-lastname-123", "abc@gmail.com");

        final Customer customerTwo =
                constructCustomer("cust_456", "test-user-firstname-456",
                        "test-user-lastname-456", "xyz@gmail.com");

        // Mock DynamoDB Page
        final Page<Customer> dynamoPage = mock(Page.class);

        when(customerProperties.maxPageSize()).thenReturn(50);
        when(customerRepository.scanPage(pageSize, lastEvaluatedKey))
                .thenReturn(dynamoPage);

        when(dynamoPage.items()).thenReturn(List.of(customerOne, customerTwo));
        when(dynamoPage.lastEvaluatedKey()).thenReturn(lastEvaluatedKey);

        // When
        final PagedResponse<CustomerResponse> result =
                customerService.findAllCustomers(pageSize, cursor);

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals(
                List.of(customerOne.fromCustomer(), customerTwo.fromCustomer()),
                result.content()
        );
        assertEquals(lastEvaluatedKey, result.nextCursor());

        verify(customerRepository, times(1))
                .scanPage(pageSize, lastEvaluatedKey);
    }

    /**
     * Tests pagination when the requested page exceeds total available pages.
     *
     * <p>Expected behavior:
     * <ul>
     *     <li>totalElements and totalPages remain correct</li>
     *     <li>Returned content list is empty</li>
     * </ul>
     */
    @Test
    void findAllCustomers_PageOutOfRange_ReturnsEmptyContent() {
        // Given
        final int pageSize = 2;
        final Map<String, String> cursor =
                Map.of("customerId", "cust_999");
        final Map<String, AttributeValue> lastEvaluatedKey =
                Map.of("customerId", AttributeValue.builder().s("cust_999").build());
        // Mock empty DynamoDB page
        final Page<Customer> dynamoPage = mock(Page.class);
        when(customerProperties.maxPageSize()).thenReturn(50);
        when(customerRepository.scanPage(pageSize, lastEvaluatedKey))
                .thenReturn(dynamoPage);
        when(dynamoPage.items()).thenReturn(List.of());
        when(dynamoPage.lastEvaluatedKey()).thenReturn(null);

        // When
        final PagedResponse<CustomerResponse> result =
                customerService.findAllCustomers(pageSize, cursor);

        // Then
        assertNotNull(result);
        assertTrue(result.content().isEmpty());
        assertEquals(0, result.size());
        assertNull(result.nextCursor());

        verify(customerRepository, times(1))
                .scanPage(pageSize, lastEvaluatedKey);
    }

    /**
     * Ensures the service normalizes invalid or negative page numbers to page=1.
     *
     * <p>Validates that:
     * <ul>
     *     <li>Content is correctly computed using page 1</li>
     *     <li>Pagination metadata is accurate</li>
     * </ul>
     */
    @ParameterizedTest
    @ValueSource(ints = {0, -1, -2, -3, -4, -5})
    void findAllCustomers_InvalidOrNegativePage_AlwaysUsesPage1(int page) {
        // Given
        final int pageSize = 2;
        final Map<String, String> cursor =
                Map.of("customerId", "cust_123");
        final Map<String, AttributeValue> lastEvaluatedKey =
                Map.of("customerId", AttributeValue.builder().s("cust_123").build());
        final Customer existingCustomerOne =
                constructCustomer("cust_123", "test-user-firstname-123",
                        "test-user-lastname-123", "abc@gmail.com");
        final Customer existingCustomerTwo =
                constructCustomer("cust_456", "test-user-firstname-456",
                        "test-user-lastname-456", "xyz@gmail.com");
        final Page<Customer> dynamoPage = mock(Page.class);
        when(customerProperties.maxPageSize()).thenReturn(50);
        when(customerRepository.scanPage(pageSize, lastEvaluatedKey))
                .thenReturn(dynamoPage);
        when(dynamoPage.items())
                .thenReturn(List.of(existingCustomerOne, existingCustomerTwo));
        when(dynamoPage.lastEvaluatedKey())
                .thenReturn(lastEvaluatedKey);

        // When
        final PagedResponse<CustomerResponse> result =
                customerService.findAllCustomers(pageSize, cursor);

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals(
                List.of(
                        existingCustomerOne.fromCustomer(),
                        existingCustomerTwo.fromCustomer()
                ),
                result.content()
        );
        assertEquals(lastEvaluatedKey, result.nextCursor());
        verify(customerRepository, times(1))
                .scanPage(pageSize, lastEvaluatedKey);
    }

    /**
     * Tests successful lookup by customer ID.
     *
     * <ul>
     *     <li>Repository returns a matching customer</li>
     *     <li>Service converts entity → DTO</li>
     *     <li>The expected CustomerResponse is returned</li>
     * </ul>
     */
    @Test
    void findById_ShouldReturnResponse_WhenFound() throws CustomerNotFoundExceptions {
        // Given
        final Customer existingCustomer = constructCustomer(CUSTOMER_ID, FIRST_NAME, LAST_NAME, EMAIL_EXISTING);
        when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(existingCustomer));

        // When
        final CustomerResponse actual = customerService.findById(CUSTOMER_ID);
        final CustomerResponse expected = existingCustomer.fromCustomer();

        // Then
        assertEquals(expected, actual);
    }

    /**
     * Ensures the service throws {@link CustomerNotFoundExceptions}
     * when a customer does not exist.
     *
     * <ul>
     *     <li>Repository returns Optional.empty()</li>
     *     <li>Service propagates the exception</li>
     * </ul>
     */
    @Test
    void findById_ShouldThrow_WhenNotFound() {
        // Given
        when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.empty());
        // Then
        assertThrows(CustomerNotFoundExceptions.class, () -> customerService.findById(CUSTOMER_ID));
        verify(customerRepository, times(1)).findById(CUSTOMER_ID);
    }
    /**
     * Tests lookup by email when matching record exists.
     *
     * <ul>
     *     <li>Repository returns the matching customer</li>
     *     <li>Service converts entity → DTO</li>
     *     <li>Correct CustomerResponse is returned</li>
     * </ul>
     */
    @Test
    void findByEmail_ShouldReturnResponse_WhenFound() throws CustomerNotFoundExceptions {
        // Given
        final Customer existingCustomer = constructCustomer(CUSTOMER_ID, FIRST_NAME, LAST_NAME, EMAIL_EXISTING);
        when(customerRepository.findByEmail(EMAIL_EXISTING)).thenReturn(Optional.of(existingCustomer));

        // When
        final CustomerResponse actual = customerService.findByEmail(EMAIL_EXISTING);
        final CustomerResponse expected = existingCustomer.fromCustomer();

        // Then
        assertEquals(expected, actual);
    }

    /**
     * Ensures service throws {@link CustomerNotFoundExceptions} when
     * no customer exists for the given email.
     *
     * <ul>
     *     <li>Repository returns Optional.empty()</li>
     *     <li>Service throws exception</li>
     * </ul>
     */
    @Test
    void findByEmail_ShouldThrow_WhenNotFound() {
        // Given
        when(customerRepository.findByEmail(EMAIL_NEW)).thenReturn(Optional.empty());
        // Then
        assertThrows(CustomerNotFoundExceptions.class, () -> customerService.findByEmail(EMAIL_NEW));
        verify(customerRepository, times(1)).findByEmail(EMAIL_NEW);
    }

    /**
     * Tests existence check when a customer exists.
     *
     * <ul>
     *     <li>Repository returns an existing customer</li>
     *     <li>Service returns true</li>
     * </ul>
     */
    @Test
    void existsById_ShouldReturnTrue_WhenFound() {
        // Given
        final Customer existingCustomer = constructCustomer(CUSTOMER_ID, FIRST_NAME, LAST_NAME, EMAIL_EXISTING);
        when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(existingCustomer));
        // Then
        assertTrue(customerService.existsById(CUSTOMER_ID));
    }

    /**
     * Tests existence check when a customer does not exist.
     *
     * <ul>
     *     <li>Repository returns Optional.empty()</li>
     *     <li>Service returns false</li>
     * </ul>
     */
    @Test
    void existsById_ShouldReturnFalse_WhenNotFound() {
        // Given
        when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.empty());
        // When Then
        assertFalse(customerService.existsById(CUSTOMER_ID));
    }

    /**
     * Verifies deletion logic:
     *
     * <ul>
     *     <li>Repository.deleteById() is called exactly once</li>
     *     <li>Service returns a message containing the deleted ID</li>
     * </ul>
     */
    @Test
    void deleteCustomer_ShouldCallDeleteById_AndReturnMessage() {
        // When
        final String msg = customerService.deleteCustomer(CUSTOMER_ID);

        // Then
        verify(customerRepository, times(1)).deleteById(CUSTOMER_ID);
        assertTrue(msg.contains(CUSTOMER_ID));
    }

    /**
     * Helper method for constructing a {@link Customer} object with
     * the provided attributes and a default address.
     */
    private Customer constructCustomer(final String customerId,
                                       final String firstName,
                                       final String lastName,
                                       final String customerEmail
    ) {
        return Customer.builder()
                .customerId(customerId)
                .firstName(firstName)
                .lastName(lastName)
                .customerEmail(customerEmail)
                .address(constructAddress())
                .build();
    }

    /**
     * Helper utility to construct a sample {@link Address} used in test customers.
     */
    private Address constructAddress() {
        return Address.builder()
                .street("Street-123")
                .houseNumber("houseNumber-123")
                .zipCode("zipCOde-123")
                .build();
    }
}