package com.forsaken.ecommerce.order.orderline.repository;


import com.forsaken.ecommerce.order.order.model.Order;
import com.forsaken.ecommerce.order.orderline.model.OrderLine;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JPA slice tests for {@link OrderLineRepository}.
 *
 * <p>
 * This test suite verifies the correctness of Spring Data JPA query derivation
 * for {@code OrderLine} entities using an in-memory H2 database.
 * </p>
 *
 * <p>
 * <b>Testing scope:</b>
 * </p>
 * <ul>
 *     <li>Only JPA components are loaded using {@link DataJpaTest}.</li>
 *     <li>Entity mappings and relationships are validated.</li>
 *     <li>Real SQL is generated and executed against H2.</li>
 * </ul>
 *
 * <p>
 * These tests ensure that repository methods relying on property-path
 * navigation (e.g. {@code order.reference}) behave correctly at runtime.
 * </p>
 */
@DataJpaTest
class OrderLineRepositoryTest {

    @Autowired
    private OrderLineRepository orderLineRepository;

    @Autowired
    private TestEntityManager entityManager;

    /**
     * Verifies that {@link OrderLineRepository#findAllByOrder_Reference(String, Pageable)}
     * returns all {@link OrderLine} entities associated with the specified
     * order reference.
     *
     * <p>
     * This test validates:
     * </p>
     * <ul>
     *     <li>Correct traversal of the {@code OrderLine → Order} relationship.</li>
     *     <li>Accurate filtering using the {@code reference} field of the
     *     associated {@link Order}.</li>
     *     <li>Proper pagination support at the persistence layer.</li>
     * </ul>
     *
     * <p>
     * The database state is explicitly set up using {@link TestEntityManager}
     * to ensure deterministic and repeatable results.
     * </p>
     */
    @Test
    void shouldReturnOrderLinesForGivenOrderReference() {
        // given
        final Order order = constructOrder("ORD-123");
        entityManager.persist(order);

        final OrderLine orderLineOne = constructOrderLine(order, 101, 2);
        final OrderLine orderLineTwo = constructOrderLine(order, 102, 1);

        entityManager.persist(orderLineOne);
        entityManager.persist(orderLineTwo);
        entityManager.flush();
        final Pageable pageable = PageRequest.of(0, 10);

        // when
        final Page<OrderLine> result =
                orderLineRepository.findAllByOrder_Reference("ORD-123", pageable);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent()).hasSize(2);
    }

    /**
     * Constructs an {@link OrderLine} entity for testing purposes.
     *
     * <p>
     * This helper method centralizes entity creation logic to improve test
     * readability and reduce duplication.
     * </p>
     *
     * @param order     the owning {@link Order}; must not be {@code null}
     * @param productId the product identifier associated with the order line
     * @param quantity  the quantity of the product ordered
     * @return a fully initialized {@link OrderLine} instance
     */
    private OrderLine constructOrderLine(
            final Order order,
            final Integer productId,
            final int quantity
    ) {
        return OrderLine.builder()
                .order(order)
                .productId(productId)
                .quantity(quantity)
                .build();
    }

    /**
     * Constructs an {@link Order} entity with the given reference.
     *
     * <p>
     * Only fields required for repository testing are populated.
     * Additional domain attributes are intentionally omitted to
     * keep the test focused on query behavior.
     * </p>
     *
     * @param orderReference the unique reference identifier of the order
     * @return a minimally initialized {@link Order} entity
     */
    private Order constructOrder(final String orderReference) {
        return Order.builder()
                .reference(orderReference)
                .build();
    }
}