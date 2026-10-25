package com.forsaken.ecommerce.order.order.repository;

import com.forsaken.ecommerce.order.order.model.Order;
import com.forsaken.ecommerce.order.order.model.OrderSagaStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for performing CRUD and query operations on {@link Order} entities.
 * <p>
 * Extends {@link JpaRepository} to inherit common persistence methods and declares
 * custom query methods for retrieving orders based on customer and date range filters.
 * </p>
 */
public interface IOrderRepository extends JpaRepository<Order, Integer> {

    @Query("select o.sagaStatus from Order o where o.reference = :reference")
    Optional<OrderSagaStatus> findSagaStatusByReference(@Param("reference") String reference);

    /** Keyset page of claimable saga ids, so batch runs stay stable while statuses change underneath them. */
    @Query("""
            select o.id from Order o
            where o.sagaStatus in :statuses
              and (o.claimOwner is null or o.claimExpiresAt < :now)
              and o.id > :afterId
            order by o.id asc
            """)
    List<Integer> findClaimableIdsBySagaStatus(
            @Param("statuses") Collection<OrderSagaStatus> statuses,
            @Param("now") LocalDateTime now,
            @Param("afterId") Integer afterId,
            Pageable pageable
    );

    /** COMPLETED orders created in [from, to), aggregated per payment method. */
    @Query("""
            select o.paymentMethod as paymentMethod, count(o) as count, sum(o.totalAmount) as totalAmount
            from Order o
            where o.sagaStatus = com.forsaken.ecommerce.order.order.model.OrderSagaStatus.COMPLETED
              and o.createdDate >= :from and o.createdDate < :to
            group by o.paymentMethod
            """)
    List<OrderSalesSummary> summarizeCompletedBetween(
            @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** Keyset page of order ids in a saga status created before the cutoff, for reconciliation. */
    @Query("""
            select o.id from Order o
            where o.sagaStatus = :status and o.createdDate < :before and o.id > :afterId
            order by o.id asc
            """)
    List<Integer> findIdsBySagaStatusCreatedBefore(
            @Param("status") OrderSagaStatus status,
            @Param("before") LocalDateTime before,
            @Param("afterId") Integer afterId,
            Pageable pageable
    );

    @Query("select o.sagaStatus from Order o where o.id = :id")
    Optional<OrderSagaStatus> findSagaStatusById(@Param("id") Integer id);

    @Query("select o.reference from Order o where o.id = :id")
    Optional<String> findReferenceById(@Param("id") Integer id);

    @EntityGraph(attributePaths = "orderLines")
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findDetailedById(@Param("id") Integer id);

    /** Atomically claims a saga whose previous claim is absent or expired. */
    @Transactional
    @Modifying
    @Query("""
            update Order o set o.claimOwner = :owner, o.claimExpiresAt = :expiresAt
            where o.id = :id and o.sagaStatus in :statuses
              and (o.claimOwner is null or o.claimExpiresAt < :now)
            """)
    int claim(
            @Param("id") Integer id,
            @Param("owner") String owner,
            @Param("now") LocalDateTime now,
            @Param("expiresAt") LocalDateTime expiresAt,
            @Param("statuses") Collection<OrderSagaStatus> statuses
    );

    /** Moves the saga to the next status only while the caller still holds an unexpired claim. */
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("""
            update Order o set o.sagaStatus = :to
            where o.id = :id and o.sagaStatus = :from
              and o.claimOwner = :owner and o.claimExpiresAt >= :now
            """)
    int transition(
            @Param("id") Integer id,
            @Param("owner") String owner,
            @Param("now") LocalDateTime now,
            @Param("from") OrderSagaStatus from,
            @Param("to") OrderSagaStatus to
    );

    @Transactional
    @Modifying
    @Query("""
            update Order o set o.claimOwner = null, o.claimExpiresAt = null
            where o.id = :id and o.claimOwner = :owner
            """)
    int releaseClaim(@Param("id") Integer id, @Param("owner") String owner);

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
