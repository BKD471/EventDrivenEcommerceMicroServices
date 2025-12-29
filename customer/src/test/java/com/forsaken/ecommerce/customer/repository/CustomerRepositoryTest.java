package com.forsaken.ecommerce.customer.repository;


import com.forsaken.ecommerce.customer.configs.dynamodb.DynamoDbProperties;
import com.forsaken.ecommerce.customer.model.Customer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.PageIterable;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CustomerRepository}, ensuring that all DynamoDB Enhanced Client
 * operations are invoked correctly and that the repository behaves as expected when
 * interacting with DynamoDB tables, indexes, and query results.
 *
 * <p>This test suite validates repository behavior by mocking:
 * <ul>
 *     <li>{@link DynamoDbEnhancedClient}</li>
 *     <li>{@link DynamoDbTable}</li>
 *     <li>{@link DynamoDbIndex}</li>
 *     <li>DynamoDB Page and PageIterable flows</li>
 * </ul>
 *
 * <p>The repository design uses the DynamoDB Enhanced Client pattern with Consumers
 * passed into operations like:
 * <ul>
 *     <li>{@code getItem(Consumer)}</li>
 *     <li>{@code deleteItem(Consumer)}</li>
 *     <li>{@code query(Consumer)}</li>
 * </ul>
 *
 * These tests verify that:
 * <ul>
 *     <li>The correct Enhanced Client methods are invoked</li>
 *     <li>Consumer request-builders are passed and utilized</li>
 *     <li>Optional return types behave consistently</li>
 *     <li>Scans, queries, and index access work as expected</li>
 * </ul>
 */
public class CustomerRepositoryTest {

    private static final String CUSTOMER_ID = "cust-123";
    private static final String CUSTOMER_EMAIL = "abc@gmail.com";

    private DynamoDbEnhancedClient enhancedClient;
    private DynamoDbTable<Customer> customerTable;
    private DynamoDbIndex<Customer> emailIndex;
    private DynamoDbProperties properties;
    private CustomerRepository repository;

    /**
     * Initializes all mocked DynamoDB components and constructs a {@link CustomerRepository}
     * instance before each test.
     *
     * <p>Also stubs the table name lookup and ensures that calling
     * {@code enhancedClient.table(...)} returns the mocked {@link DynamoDbTable}.
     */
    @BeforeEach
    void setup() {
        enhancedClient = mock(DynamoDbEnhancedClient.class);
        customerTable = mock(DynamoDbTable.class);
        emailIndex = mock(DynamoDbIndex.class);
        properties = mock(DynamoDbProperties.class);

        when(properties.tableName()).thenReturn("customer-table");
        when(enhancedClient.table(eq("customer-table"), any(TableSchema.class)))
                .thenReturn(customerTable);
        repository = new CustomerRepository(enhancedClient, properties);
    }

    /**
     * Verifies that calling {@link CustomerRepository#save(Customer)} delegates to
     * {@link DynamoDbTable#putItem(Object)} exactly once with the expected customer object.
     */
    @Test
    void testSave() {
        // Given
        final Customer customer = constructCustomer(CUSTOMER_ID);

        // When
        repository.save(customer);

        // Then
        verify(customerTable, times(1)).putItem(customer);
    }

    /**
     * Tests successful lookup of a customer by ID.
     *
     * <p>Validates that:
     * <ul>
     *     <li>A Consumer is passed to {@code getItem(Consumer)}</li>
     *     <li>The mocked table returns the expected Customer</li>
     *     <li>The repository returns {@link Optional#of(Object)}</li>
     * </ul>
     */
    @Test
    void testFindById_Found() {
        // Given
        final Customer mockCustomer = constructCustomer(CUSTOMER_ID);

        // When
        // Capture the consumer passed to getItem and stub getItem using the captor
        final ArgumentCaptor<Consumer> captor = ArgumentCaptor.forClass(Consumer.class);
        when(customerTable.getItem(captor.capture())).thenReturn(mockCustomer);
        final Optional<Customer> result = repository.findById(CUSTOMER_ID);

        // Then
        assertTrue(result.isPresent());
        assertEquals(CUSTOMER_ID, result.get().getCustomerId());
        // Additional verification: ensure a Consumer was passed into getItem (i.e. repository built the request)
        final Consumer captured = captor.getValue();
        assertNotNull(captured);
    }

    /**
     * Tests repository behavior when {@code getItem()} returns null,
     * meaning the customer does not exist.
     *
     * <p>Ensures that:
     * <ul>
     *     <li>An appropriate Consumer was still supplied</li>
     *     <li>{@link Optional#empty()} is returned</li>
     * </ul>
     */
    @Test
    void testFindById_NotFound() {
        // When
        final ArgumentCaptor<Consumer> captor = ArgumentCaptor.forClass(Consumer.class);
        when(customerTable.getItem(captor.capture())).thenReturn(null);
        final Optional<Customer> result = repository.findById(CUSTOMER_ID);

        // Then
        assertFalse(result.isPresent());
        final Consumer captured = captor.getValue();
        assertNotNull(captured);
    }

    /**
     * Tests that the repository correctly performs a full table scan using
     * {@link DynamoDbTable#scan()} and aggregates results from {@link PageIterable}.
     *
     * <p>Validates that:
     * <ul>
     *     <li>Pages and iterators are mocked correctly</li>
     *     <li>Each page yields the expected Customer objects</li>
     *     <li>The final list contains all items in order</li>
     * </ul>
     */
    @Test
    void testFindAll() {
        // Given
        final Customer customerOne = constructCustomer("cust-123");
        final Customer customerTwo = constructCustomer("cust-456");

        // When
        // Mock Page
        final Page<Customer> page = mock(Page.class);
        when(page.items()).thenReturn(List.of(customerOne, customerTwo));

        // Mock PageIterable (actual return type from scan())
        final PageIterable<Customer> iterable = mock(PageIterable.class);
        // Mock iterator<Page<Customer>>
        final Iterator<Page<Customer>> it = mock(Iterator.class);
        when(it.hasNext()).thenReturn(true, false);
        when(it.next()).thenReturn(page);
        // When iterable.iterator() -> our mocked iterator
        when(iterable.iterator()).thenReturn(it);
        // scan() must return PageIterable<Customer>
        when(customerTable.scan()).thenReturn(iterable);
        final List<Customer> all = repository.findAll();

        // Then
        assertEquals(2, all.size());
        assertEquals("cust-123", all.get(0).getCustomerId());
        assertEquals("cust-456", all.get(1).getCustomerId());
    }

    /**
     * Verifies that the repository calls {@code deleteItem(Consumer)} exactly once
     * when deleting a customer by ID.
     *
     * <p>The test also captures the Consumer to ensure the repository properly builds
     * the DynamoDB key request.
     */
    @Test
    void testDeleteById() {
        // When
        final ArgumentCaptor<Consumer> captor = ArgumentCaptor.forClass(Consumer.class);
        repository.deleteById(CUSTOMER_ID);

        // Then
        // Verify deleteItem was called exactly once and capture the Consumer argument
        verify(customerTable, times(1)).deleteItem(captor.capture());
        // Verify repository actually supplied a Consumer
        final Consumer captured = captor.getValue();
        assertNotNull(captured);
    }

    /**
     * Tests lookup by email using the DynamoDB secondary index "email-index".
     *
     * <p>Validates that:
     * <ul>
     *     <li>The repository accesses the correct index</li>
     *     <li>A Consumer is passed to {@code query(Consumer)}</li>
     *     <li>A page containing a single matching customer is processed</li>
     *     <li>The result is {@link Optional#of(Object)}</li>
     * </ul>
     */
    @Test
    void testFindByEmail_Found() {
        // Given
        final Customer customer = constructCustomer(CUSTOMER_ID);

        // When
        when(customerTable.index("email-index")).thenReturn(emailIndex);
        final Page<Customer> page = mock(Page.class);
        when(page.items()).thenReturn(List.of(customer));

        final SdkIterable<Page<Customer>> iterable = mock(SdkIterable.class);
        when(iterable.iterator()).thenReturn(List.of(page).iterator());

        // Capture the Consumer passed into emailIndex.query(...)
        final ArgumentCaptor<Consumer> captor = ArgumentCaptor.forClass(Consumer.class);
        when(emailIndex.query(captor.capture())).thenReturn(iterable);
        final Optional<Customer> result = repository.findByEmail("abc@gmail.com");

        // Then
        assertTrue(result.isPresent());
        assertEquals(CUSTOMER_ID, result.get().getCustomerId());
        // Verify repository actually supplied a Consumer
        assertNotNull(captor.getValue());
    }

    /**
     * Tests email lookup behavior when the index query returns an empty result.
     *
     * <p>Ensures that:
     * <ul>
     *     <li>The index is used</li>
     *     <li>A Consumer request-builder was passed into {@code query()}</li>
     *     <li>No items were found, resulting in {@link Optional#empty()}</li>
     * </ul>
     */
    @Test
    void testFindByEmail_NotFound() {
        // When
        when(customerTable.index("email-index")).thenReturn(emailIndex);
        final Page<Customer> page = mock(Page.class);
        when(page.items()).thenReturn(List.of());

        final SdkIterable<Page<Customer>> iterable = mock(SdkIterable.class);
        when(iterable.iterator()).thenReturn(List.of(page).iterator());
        // Capture the Consumer passed into emailIndex.query(...) instead of using any(...)
        final ArgumentCaptor<Consumer> captor = ArgumentCaptor.forClass(Consumer.class);
        when(emailIndex.query(captor.capture())).thenReturn(iterable);
        final Optional<Customer> result = repository.findByEmail("xyz@gmail.com");

        // Then
        assertFalse(result.isPresent());
        // Verify repository actually supplied a Consumer
        assertNotNull(captor.getValue());
    }

    /**
     * Verifies that {@link CustomerRepository#scanPage(int, Map)} returns
     * the first page of results from a DynamoDB scan operation.
     *
     * <p>
     * This test simulates a DynamoDB Enhanced Client scan using mocked
     * {@link DynamoDbTable},
     * {@link PageIterable},
     * and {@link Page} objects.
     * </p>
     *
     * <p><b>Test Scenario:</b></p>
     * <ul>
     *   <li>A scan request is issued with a limit of {@code 2} items.</li>
     *   <li>A non-null {@code lastEvaluatedKey} is provided to simulate pagination.</li>
     *   <li>The DynamoDB table scan returns an iterable containing at least one page.</li>
     * </ul>
     *
     * <p><b>Mocked Behavior:</b></p>
     * <ul>
     *   <li>{@code enhancedClient.table(...)} returns a mocked DynamoDB table.</li>
     *   <li>{@code table.scan(...)} returns a mocked {@link PageIterable}.</li>
     *   <li>The iterator of the iterable returns a single mocked page.</li>
     * </ul>
     *
     * <p><b>Expected Outcome:</b></p>
     * <ul>
     *   <li>The repository method returns the first {@link Page} from the scan result.</li>
     *   <li>The scan operation is invoked exactly once.</li>
     * </ul>
     *
     * <p>
     * This test ensures that the repository correctly handles DynamoDB scan pagination
     * and does not attempt to process additional pages beyond the first.
     * </p>
     */
    @Test
    void scanPage_ShouldReturnFirstDynamoDbPage() {
        // Given
        final int limit = 2;
        final Map<String, AttributeValue> lastEvaluatedKey =
                Map.of("customerId", AttributeValue.builder().s("cust_123").build());

        @SuppressWarnings("unchecked")
        final PageIterable<Customer> pageIterable = mock(PageIterable.class);
        @SuppressWarnings("unchecked")
        final Iterator<Page<Customer>> iterator = mock(Iterator.class);
        @SuppressWarnings("unchecked")
        final Page<Customer> dynamoPage = mock(Page.class);

        when(properties.tableName()).thenReturn("customer-table");
        when(enhancedClient.table(eq("customer-table"), any(TableSchema.class)))
                .thenReturn(customerTable);
        when(customerTable.scan(any(Consumer.class)))
                .thenReturn(pageIterable);
        when(pageIterable.iterator()).thenReturn(iterator);
        when(iterator.hasNext()).thenReturn(true);
        when(iterator.next()).thenReturn(dynamoPage);

        // When
        final Page<Customer> result =
                repository.scanPage(limit, lastEvaluatedKey);

        // Then
        assertNotNull(result);
        assertEquals(dynamoPage, result);
        verify(customerTable).scan(any(Consumer.class));
        verify(iterator).next();
    }

    /**
     * Utility method for creating a minimal {@link Customer} test object with the
     * provided ID and default name/email attributes.
     */
    private Customer constructCustomer(final String customerId) {
        return Customer.builder().customerId(customerId).firstName("John").customerEmail(CUSTOMER_EMAIL).build();
    }
}

