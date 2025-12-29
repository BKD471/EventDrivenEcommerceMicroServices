package com.forsaken.ecommerce.customer.service;

import com.forsaken.ecommerce.common.exceptions.CustomerNotFoundExceptions;
import com.forsaken.ecommerce.common.responses.PagedResponse;
import com.forsaken.ecommerce.customer.configs.general.OrderProperties;
import com.forsaken.ecommerce.customer.dto.CustomerRequest;
import com.forsaken.ecommerce.customer.dto.CustomerResponse;
import com.forsaken.ecommerce.customer.model.Customer;
import com.forsaken.ecommerce.customer.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.apache.commons.lang.StringUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;


@Service
@Slf4j
@RequiredArgsConstructor
public class CustomerServiceImpl implements ICustomerService {

    private final CustomerRepository customerRepository;
    private final OrderProperties orderProperties;
    private final Class<?> className = CustomerServiceImpl.class;

    @Override
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
    public PagedResponse<CustomerResponse> findAllCustomers(final Integer page, final Integer size) {
        log.info("Received request to get all customers");
        final int finalPage = null != page ?
                Math.max(page - 1, 0) :
                Math.max(orderProperties.defaultPageNumber() - 1, 0);
        final int finalSize = null != size ?
                Math.min(Math.max(size, 1), orderProperties.maxPageSize()) :
                orderProperties.defaultPageSize();

        final List<CustomerResponse> customerResponses = this.customerRepository.findAll()
                .stream()
                .map(Customer::fromCustomer)
                .collect(Collectors.toList());
        final int start = finalPage * finalSize;
        final int end = Math.min(start + finalSize, customerResponses.size());

        final List<CustomerResponse> pagedContent =
                (start >= customerResponses.size()) ? List.of() : customerResponses.subList(start, end);
        final int totalPages = (int) Math.ceil((double) customerResponses.size() / finalSize);
        return PagedResponse.<CustomerResponse>builder()
                .content(pagedContent)
                .page(finalPage + 1)
                .size(finalSize)
                .totalElements(customerResponses.size())
                .totalPages(totalPages)
                .build();
    }

    @Override
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
    public boolean existsById(final String customerId) {
        log.info("Received request to check if customer with id {}", customerId);
        return this.customerRepository.findById(customerId)
                .isPresent();
    }

    @Override
    public String deleteCustomer(final String customerId) {
        log.info("Received request to delete customer with id {}", customerId);
        this.customerRepository.deleteById(customerId);
        return String.format("Deleted customer with id %s", customerId);
    }

    private void mergeCustomer(final Customer customer, final CustomerRequest request) {
        if (StringUtils.isNotBlank(request.firstname())) {
            customer.setFirstName(request.firstname());
        }
        if (StringUtils.isNotBlank(request.lastname())) {
            customer.setLastName(request.lastname());
        }
        if (StringUtils.isNotBlank(request.email())) {
            customer.setCustomerEmail(request.email());
        }
        if (request.address() != null) {
            customer.setAddress(request.address());
        }
    }
}
