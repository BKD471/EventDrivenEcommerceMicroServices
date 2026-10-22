package com.forsaken.ecommerce.payment.repository;

import com.forsaken.ecommerce.payment.model.Payment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Repository interface for managing {@link Payment} entities.
 *
 * <p>This interface extends {@link JpaRepository}, providing built-in CRUD and
 * pagination capabilities. It also defines custom query methods for retrieving
 * payment summaries and filtering payments by creation date.</p>
 *
 * <p>All custom queries follow Spring Data JPA conventions and support database-level
 * pagination for efficient data retrieval.</p>
 */
public interface IPaymentRepository extends JpaRepository<Payment, Integer> {

    Optional<Payment> findByOrderId(Integer orderId);

    /**
     * Retrieves a paginated summary of payments grouped by payment method, filtered
     * optionally by a date range.
     *
     * <p>This query aggregates results by:
     * <ul>
     *     <li><b>paymentMethod</b> — the method used (e.g., UPI, CARD, NET_BANKING)</li>
     *     <li><b>count</b> — number of payments per method</li>
     *     <li><b>totalAmount</b> — sum of transaction amounts for each method</li>
     * </ul>
     * </p>
     *
     * <p>Date filtering behavior:</p>
     * <ul>
     *     <li>If <code>fromDate</code> is null → no lower bound is applied.</li>
     *     <li>If <code>toDate</code> is null → no upper bound is applied.</li>
     * </ul>
     *
     * <p>The <b>countQuery</b> ensures correct pagination by counting only distinct
     * payment methods for the specified date filter.</p>
     *
     * @param fromDate optional start date-time for filtering payments (inclusive)
     * @param toDate   optional end date-time for filtering payments (inclusive)
     * @param pageable pagination information such as page number and size
     * @return a {@link Page} of {@link PaymentSummary} projections containing aggregated results
     */
    @Query(value = """
    SELECT p.paymentMethod AS paymentMethod,
           COUNT(p)        AS count,
           SUM(CAST(p.amount AS BIGDECIMAL ))   AS totalAmount
    FROM Payment p
    WHERE (:fromDate IS NULL OR p.createdDate >= :fromDate)
      AND (:toDate   IS NULL OR p.createdDate <= :toDate)
      AND (p.status IS NULL OR
           p.status = com.forsaken.ecommerce.payment.model.PaymentStatus.COMPLETED)
    GROUP BY p.paymentMethod
    ORDER BY p.paymentMethod
    """,
            countQuery = """
    SELECT COUNT(DISTINCT p.paymentMethod)
    FROM Payment p
    WHERE (:fromDate IS NULL OR p.createdDate >= :fromDate)
      AND (:toDate   IS NULL OR p.createdDate <= :toDate)
      AND (p.status IS NULL OR
           p.status = com.forsaken.ecommerce.payment.model.PaymentStatus.COMPLETED)
    """)
    Page<PaymentSummary> findPaymentSummaryBetween(
            @Param("fromDate") final LocalDateTime fromDate,
            @Param("toDate") final LocalDateTime toDate,
            final Pageable pageable
    );

    /**
     * Retrieves a paginated list of payments whose {@code createdDate}
     * falls between the specified {@code fromDate} and {@code toDate}.
     *
     * <p>Both boundaries are inclusive. This method requires non-null values for both
     * date parameters. If you need open-ended filters, use the summary query instead.</p>
     *
     * @param fromDate the start of the date range filter (inclusive)
     * @param toDate   the end of the date range filter (inclusive)
     * @param pageable pagination details such as page index and size
     * @return a {@link Page} of {@link Payment} entities filtered by the given range
     */
    Page<Payment> findAllByCreatedDateBetween(
            final LocalDateTime fromDate,
            final LocalDateTime toDate,
            final Pageable pageable
    );
}
