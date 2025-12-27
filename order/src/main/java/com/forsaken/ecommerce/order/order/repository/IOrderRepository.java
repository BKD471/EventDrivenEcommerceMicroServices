package com.forsaken.ecommerce.order.order.repository;

import com.forsaken.ecommerce.order.order.model.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository interface for performing CRUD and query operations on {@link Order} entities.
 * <p>
 * Extends {@link JpaRepository} to inherit common persistence methods and declares
 * custom query methods for retrieving orders based on customer and date range filters.
 * </p>
 */
public interface IOrderRepository extends JpaRepository<Order, Integer> {

    /**
     * Retrieves all {@link Order} records for the given customer within the specified date range.
     *
     * <p>This method uses Spring Data JPA's derived query mechanism to generate
     * a query equivalent to:</p>
     *
     * <pre>
     * SELECT o
     * FROM Order o
     * WHERE o.customerId = :customerId
     *   AND o.createdDate BETWEEN :fromDate AND :toDate
     * </pre>
     *
     * @param customerId the ID of the customer whose orders should be fetched;
     *                   must not be {@code null}.
     * @param fromDate   the start of the date range (inclusive); if {@code null},
     *                   Spring Data may throw an error depending on null handling.
     * @param toDate     the end of the date range (inclusive); if {@code null},
     *                   Spring Data may throw an error depending on null handling.
     * @return a list of all matching {@link Order} entities; never {@code null}
     * (but may be empty).
     */
    Page<Order> findAllByCustomerIdAndCreatedDateBetween(
            final String customerId,
            final LocalDateTime fromDate,
            final LocalDateTime toDate,
            final Pageable pageable
    );
}
