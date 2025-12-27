package com.forsaken.ecommerce.order.order.repository;

import com.forsaken.ecommerce.order.order.model.Order;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository tests for {@link IOrderRepository}.
 *
 * <p>
 * This test class verifies the behavior of Spring Data JPA query methods
 * using an in-memory database. The tests run with a minimal application
 * context provided by {@link DataJpaTest}.
 * </p>
 *
 * <p>
 * Scope of these tests:
 * </p>
 * <ul>
 *   <li>Correct query derivation based on method names</li>
 *   <li>Filtering by customer ID</li>
 *   <li>Filtering by creation date range</li>
 *   <li>Pagination behavior</li>
 * </ul>
 *
 * <p>
 * Out of scope:
 * </p>
 * <ul>
 *   <li>Service-layer business logic</li>
 *   <li>Controller request/response handling</li>
 *   <li>Security and authorization</li>
 * </ul>
 */
@DataJpaTest
class OrderRepositoryTest {

    @Autowired
    private IOrderRepository orderRepository;

    /**
     * Verifies that orders are returned correctly when filtering by:
     * <ul>
     *   <li>a specific customer ID</li>
     *   <li>a creation date range</li>
     *   <li>pagination parameters</li>
     * </ul>
     *
     * <p>
     * The test data includes:
     * </p>
     * <ul>
     *   <li>Three orders for the same customer within the date range</li>
     *   <li>One order for a different customer outside the filter criteria</li>
     * </ul>
     *
     * <p>
     * Expected outcome:
     * </p>
     * <ul>
     *   <li>Only the three matching orders are returned</li>
     *   <li>The result is paginated correctly</li>
     * </ul>
     */
    @Test
    void shouldReturnOrdersForCustomerWithinDateRangeWithPagination() {
        // given
        final String customerId = "CUST-1";
        final LocalDateTime now = LocalDateTime.now();
        final Order order1 = constructOrder(customerId, "ORDER-1", now.minusDays(5));
        final Order order2 = constructOrder(customerId, "ORDER-2", now.minusDays(2));
        final Order order3 = constructOrder(customerId, "ORDER-3", now.minusDays(4));
        final Order otherCustomerOrder =
                constructOrder("CUST-2", "ORDER-4", now.minusDays(30));

        orderRepository.save(order1);
        orderRepository.save(order2);
        orderRepository.save(order3);
        orderRepository.save(otherCustomerOrder);
        final Pageable pageable = PageRequest.of(0, 10);

        // when
        final Page<Order> result =
                orderRepository.findAllByCustomerIdAndCreatedDateBetween(
                        customerId,
                        now.minusDays(15),
                        now.plusDays(10),
                        pageable
                );

        // then
        assertThat(result).isNotNull();
        assertThat(result.getTotalElements()).isEqualTo(3);
        assertThat(result.getContent()).hasSize(3);
        assertThat(result.getContent())
                .extracting(Order::getReference)
                .containsExactlyInAnyOrder("ORDER-1", "ORDER-2","ORDER-3");
        assertThat(result.getContent())
                .allMatch(order -> order.getCustomerId().equals(customerId));
    }

    /**
     * Helper method to create an {@link Order} entity with the
     * minimum required fields for repository testing.
     *
     * @param customerId the customer identifier
     * @param reference  the unique order reference
     * @param createdDate the creation timestamp of the order
     * @return a populated {@link Order} entity
     */
    private Order constructOrder(
            final String customerId,
            final String reference,
            final LocalDateTime createdDate
    ) {
        return Order.builder()
                .customerId(customerId)
                .reference(reference)
                .createdDate(createdDate)
                .build();
    }
}
