package com.forsaken.ecommerce.customer.repository;

import com.forsaken.ecommerce.customer.configs.dynamodb.DynamoDbProperties;
import com.forsaken.ecommerce.customer.model.Customer;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Repository class for performing CRUD and query operations on the {@link Customer}
 * entity using AWS DynamoDB via the DynamoDB Enhanced Client.
 *
 * <p>This repository encapsulates all data-access logic and provides clean,
 * type-safe methods for interacting with DynamoDB tables and secondary indexes.
 *
 * <h2>Key Responsibilities:</h2>
 * <ul>
 *     <li>Persisting Customer records</li>
 *     <li>Querying Customers by ID (partition key)</li>
 *     <li>Querying Customers by email using a Global Secondary Index (GSI)</li>
 *     <li>Scanning the entire table for retrieving all customers</li>
 *     <li>Deleting customer records by ID</li>
 * </ul>
 *
 * <p>DynamoDB interactions are performed using {@link DynamoDbEnhancedClient},
 * which provides a higher-level, schema-aware API for table and index operations.
 *
 * <p>The repository uses:
 * <ul>
 *     <li>{@code DynamoDbTable<Customer>} for table operations</li>
 *     <li>{@code DynamoDbIndex<Customer>} for querying secondary indexes</li>
 *     <li>{@code TableSchema.fromBean(Customer.class)} for attribute mapping</li>
 * </ul>
 *
 * <p>This repository abstracts away AWS-specific boilerplate code and exposes
 * simple Java methods to the service layer.
 */
@Repository
public class CustomerRepository {

    private final DynamoDbTable<Customer> customerTable;
    private final DynamoDbEnhancedClient client;
    private final DynamoDbProperties dynamoDbProperties;
    private final Class<?> className = Customer.class;

    /**
     * Initializes the repository by wiring the enhanced DynamoDB client and
     * deriving the mapped table schema for {@link Customer}.
     *
     * @param enhancedClient       the enhanced DynamoDB client instance
     * @param dynamoDbProperties   configuration properties such as table name
     */
    public CustomerRepository(
            final DynamoDbEnhancedClient enhancedClient,
            final DynamoDbProperties dynamoDbProperties
    ) {
        this.dynamoDbProperties = dynamoDbProperties;
        this.client = enhancedClient;
        this.customerTable = client.table(dynamoDbProperties.tableName(), TableSchema.fromBean(Customer.class));
    }

    /**
     * Saves or updates the given {@link Customer} record in DynamoDB.
     *
     * <p>Internally uses DynamoDB's <b>PutItem</b> operation which:
     * <ul>
     *     <li>Inserts the item if it does not exist</li>
     *     <li>Overwrites the item if it already exists</li>
     * </ul>
     *
     * @param customer the customer entity to persist
     */
    public void save(final Customer customer) {
        customerTable.putItem(customer);
    }

    /**
     * Retrieves a {@link Customer} by its primary key (customerId).
     *
     * <p>Uses a key lookup operation which is highly efficient in DynamoDB.
     *
     * @param customerId the partition key value
     * @return an {@link Optional} containing the matching Customer if present
     */
    public Optional<Customer> findById(final String customerId) {
        return Optional.ofNullable(customerTable.getItem(r -> r.key(k -> k.partitionValue(customerId))));
    }

    /**
     * Retrieves all customer records by performing a full table scan.
     *
     * <p>A scan iterates through all partitions and items inside the table, and
     * should be used sparingly in production environments due to cost and performance
     * considerations.
     *
     * @return a list of all {@link Customer} records found
     */
    public List<Customer> findAll() {
        final List<Customer> customers = new ArrayList<>();
        final SdkIterable<Page<Customer>> pages = customerTable.scan();
        for (Page<Customer> page : pages) customers.addAll(page.items());
        return customers;
    }

    /**
     * Deletes a customer from DynamoDB based on its primary key.
     *
     * <p>Internally triggers a DynamoDB <b>DeleteItem</b> operation.
     *
     * @param customerId the identifier of the customer to delete
     */
    public void deleteById(final String customerId) {
        customerTable.deleteItem(r -> r.key(k -> k.partitionValue(customerId)));
    }

    /**
     * Retrieves a {@link Customer} by their email address using a
     * DynamoDB Global Secondary Index (GSI).
     *
     * <p>Index Name: <b>email-index</b><br>
     * Partition Key: <b>customerEmail</b>
     *
     * <p>The method performs a GSI query operation, which is optimized for
     * equality lookups.
     *
     * @param customerEmail the email address to search for
     * @return an {@link Optional} containing the first matching customer, or empty if none found
     */
    public Optional<Customer> findByEmail(final String customerEmail) {
        final DynamoDbIndex<Customer> emailIndex = customerTable.index("email-index");
        SdkIterable<Page<Customer>> pages = emailIndex.query(r ->
                r.queryConditional(QueryConditional.keyEqualTo(k -> k.partitionValue(customerEmail)))
        );

        for (Page<Customer> page : pages) {
            for (Customer customer : page.items()) {
                return Optional.of(customer);
            }
        }
        return Optional.empty();
    }

    public Page<Customer> scanPage(
            final int limit,
            final Map<String, AttributeValue> lastKey
    ) {
        return customerTable.scan(r -> r
                .limit(limit)
                .exclusiveStartKey(lastKey)
        ).iterator().next();
    }
}

