package com.forsaken.ecommerce.customer.service;

import com.forsaken.ecommerce.common.exceptions.CustomerNotFoundExceptions;
import com.forsaken.ecommerce.common.responses.PagedResponse;
import com.forsaken.ecommerce.customer.configs.general.CustomerProperties;
import com.forsaken.ecommerce.customer.dto.CustomerRequest;
import com.forsaken.ecommerce.customer.dto.CustomerResponse;
import com.forsaken.ecommerce.customer.model.Customer;
import com.forsaken.ecommerce.customer.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.apache.commons.lang.StringUtils;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;


@Service
@Slf4j
@RequiredArgsConstructor
public class CustomerServiceImpl implements ICustomerService {

    private final CustomerRepository customerRepository;
    private final CustomerProperties customerProperties;
    private final Class<?> className = CustomerServiceImpl.class;

    @Override
    @CacheEvict(value = {"customers", "customerById"}, allEntries = true)
    public String createCustomer(final CustomerRequest request) throws CustomerNotFoundExceptions {
        log.info("Creating customer with request {}", request);

        final Optional<Customer> customer = customerRepository.findByEmail(request.email());
        if (customer.isPresent()) {
            throw new CustomerNotFoundExceptions(
                    String.format("Customer is already present with the provided email: %s", request.email()),
                    "createCustomer(CustomerRequest request) in " + className
            );
        }
        final String customerId = UUID.randomUUID().toString();
        customerRepository.save(request.toCustomer(customerId));
        return customerId;
    }

    @Override
    @Caching(evict = {
            @CacheEvict(value = "customerById", key = "#request.id"),
            @CacheEvict(value = "customerExists", key = "#request.id"),
            @CacheEvict(value = "customerByEmail", allEntries = true),
            @CacheEvict(value = "customers", allEntries = true)
    })
    public String updateCustomer(final CustomerRequest request) throws CustomerNotFoundExceptions {
        log.info("Received request to update customer {}", request);
        final Customer customer = this.customerRepository.findById(request.id())
                .orElseThrow(() -> new CustomerNotFoundExceptions(
                        String.format("Cannot update customer:: No customer found with the provided ID: %s", request.id()),
                        "updateCustomer(CustomerRequest request) in " + className
                ));
        mergeCustomer(customer, request);
        this.customerRepository.save(customer);
        log.info("Updated customer with id {}", customer.getCustomerId());
        return String.format("Updated customer with id %s", customer.getCustomerId());
    }

    @Override
    @Cacheable(
            value = "customers",
            key = "#size + ':' + #root.target.cursorKey(#lastEvaluatedKey)"
    )
    public PagedResponse<CustomerResponse> findAllCustomers(
            final Integer size,
            final Map<String, String> lastEvaluatedKey
    ) {
        final int finalSize = null != size
                ? Math.min(Math.max(size, 1), customerProperties.maxPageSize())
                : customerProperties.defaultPageSize();
        final Page<Customer> page =
                customerRepository.scanPage(finalSize, toAttributeValueCursor(lastEvaluatedKey));
        return PagedResponse.<CustomerResponse>builder()
                .content(page.items().stream().map(Customer::fromCustomer).toList())
                .size(page.items().size())
                .totalElements(-1L)
                .totalPages(-1)
                .nextCursor(page.lastEvaluatedKey())
                .build();
    }

    @Override
    @Cacheable(value = "customerById", key = "#customerId")
    public CustomerResponse findById(final String customerId) throws CustomerNotFoundExceptions {
        log.info("Received request to get customer by ID {}", customerId);
        return this.customerRepository.findById(customerId)
                .map(Customer::fromCustomer)
                .orElseThrow(() -> new CustomerNotFoundExceptions(
                                String.format("No customer found with the provided ID: %s", customerId),
                                "findById(final String customerId) in " + className
                        )
                );
    }

    @Override
    @Cacheable(
            value = "customerByEmail",
            key = "#customerEmail.toLowerCase()"
    )
    public CustomerResponse findByEmail(final String customerEmail) throws CustomerNotFoundExceptions {
        log.info("Received request to get customer by Email {}", customerEmail);
        return this.customerRepository.findByEmail(customerEmail)
                .map(Customer::fromCustomer)
                .orElseThrow(() -> new CustomerNotFoundExceptions(
                                String.format("No customer found with the provided Email: %s", customerEmail),
                                "findById(final String customerId) in " + className
                        )
                );
    }

    @Override
    @Cacheable(value = "customerExists", key = "#customerId")
    public boolean existsById(final String customerId) {
        log.info("Received request to check if customer with id {}", customerId);
        return this.customerRepository.findById(customerId)
                .isPresent();
    }

    @Override
    @Caching(evict = {
            @CacheEvict(value = "customerById", key = "#customerId"),
            @CacheEvict(value = "customerExists", key = "#customerId"),
            @CacheEvict(value = "customerByEmail", allEntries = true),
            @CacheEvict(value = "customers", allEntries = true)
    })
    public String deleteCustomer(final String customerId) {
        log.info("Received request to delete customer with id {}", customerId);
        this.customerRepository.deleteById(customerId);
        return String.format("Deleted customer with id %s", customerId);
    }

    /**
     * Merges non-null and non-blank fields from the incoming request
     * into the existing {@link Customer} entity.
     * <p>
     * Only fields explicitly provided in the request are updated.
     * This prevents overwriting existing values with null or blank data
     * during partial update operations.
     *
     * @param customer existing persisted customer entity
     * @param request  incoming customer update request
     */
    private void mergeCustomer(final Customer customer, final CustomerRequest request) {
        if (StringUtils.isNotBlank(request.firstname())) customer.setFirstName(request.firstname());
        if (StringUtils.isNotBlank(request.lastname())) customer.setLastName(request.lastname());
        if (StringUtils.isNotBlank(request.email())) customer.setCustomerEmail(request.email());
        if (null != request.address()) customer.setAddress(request.address());
    }

    /**
     * Converts a pagination cursor represented as a Map into a DynamoDB
     * {@link AttributeValue} map suitable for use as a {@code lastEvaluatedKey}.
     * <p>
     * Expected cursor format:
     * <pre>
     * {
     *   "customerId": "<id>"
     * }
     * </pre>
     * <p>
     * Returns {@code null} when the cursor is null or empty, indicating
     * the start of pagination.
     *
     * @param cursor pagination cursor from the API request
     * @return DynamoDB-compatible attribute value map or {@code null} for initial page
     * @throws IllegalArgumentException if the cursor format is invalid
     */
    private Map<String, AttributeValue> toAttributeValueCursor(
            final Map<String, String> cursor
    ) {
        if (null == cursor || cursor.isEmpty()) return null;
        if (!cursor.containsKey("customerId") || cursor.size() != 1)
            throw new IllegalArgumentException("Invalid cursor format");

        return Map.of(
                "customerId",
                AttributeValue.builder()
                        .s(cursor.get("customerId"))
                        .build()
        );
    }

    /**
     * Generates a deterministic string representation of a pagination cursor
     * for use as part of a cache key.
     * <p>
     * This method ensures stable cache keys for logically equivalent cursors,
     * avoiding cache misses caused by differing {@link Map} instances.
     * <p>
     * The cursor entries are sorted by key before serialization to guarantee
     * consistent ordering.
     *
     * @param lastEvaluatedKey pagination cursor map
     * @return deterministic string representation of the cursor
     */
    public String cursorKey(final Map<String, String> lastEvaluatedKey) {
        if (null == lastEvaluatedKey || lastEvaluatedKey.isEmpty()) {
            return "START";
        }

        return lastEvaluatedKey.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "=" + e.getValue())
                .reduce((a, b) -> a + "&" + b)
                .orElse("START");
    }
}
