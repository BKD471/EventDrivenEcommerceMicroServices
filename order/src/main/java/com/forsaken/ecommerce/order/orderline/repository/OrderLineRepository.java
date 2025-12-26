package com.forsaken.ecommerce.order.orderline.repository;

import com.forsaken.ecommerce.order.orderline.model.OrderLine;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository interface for performing CRUD and query operations
 * on {@link OrderLine} entities.
 *
 * <p>This interface extends {@link JpaRepository}, providing
 * built-in support for standard operations such as saving, updating,
 * deleting, and pagination.</p>
 *
 * <p>The additional method {@code findAllByOrderId(Integer)} uses
 * Spring Data JPA's derived query mechanism to fetch all order-line
 * records that belong to the given order.</p>
 *
 * @author Bhaskar
 */
public interface OrderLineRepository extends JpaRepository<OrderLine, Integer> {

    /**
     * Retrieves all {@link OrderLine} entities associated with the
     * provided order ID.
     *
     * <p>This method uses Spring Data JPA query derivation from the
     * method name. Internally, Spring generates a query equivalent to:</p>
     *
     * <pre>
     * SELECT ol
     * FROM OrderLine ol
     * WHERE ol.order.id = :orderId
     * </pre>
     *
     * @param orderReference the ID of the order whose line items should be fetched;
     *                       may be {@code null}, but in such case an empty list will
     *                       be returned or a validation exception may occur depending
     *                       on your service-layer validation.
     * @return a list of order lines belonging to the specified order;
     * never {@code null}, but may be empty.
     */
    Page<OrderLine> findAllByOrder_Reference(
            final String orderReference,
            final Pageable pageable
    );
}
