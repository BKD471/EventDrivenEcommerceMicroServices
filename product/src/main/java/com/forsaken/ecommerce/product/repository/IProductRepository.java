package com.forsaken.ecommerce.product.repository;

import com.forsaken.ecommerce.product.model.Category;
import com.forsaken.ecommerce.product.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository interface for performing CRUD operations and domain-specific queries
 * on {@link Product} entities.
 *
 * <p>This repository extends {@link JpaRepository}, providing built-in pagination,
 * sorting, and standard database operations. Additional query methods are defined
 * to support product filtering, category search, price-based search, and date-range retrieval.
 */
public interface IProductRepository extends JpaRepository<Product, Integer> {
    /**
     * Retrieves all products whose IDs are included in the given list, ordered by ID.
     *
     * <p>This is typically used for bulk retrieval during operations such as purchasing
     * or aggregating product details in a specific order.
     *
     * @param ids the list of product IDs to retrieve; must not be {@code null}
     * @return a list of {@link Product} ordered by ascending ID
     */
    List<Product> findAllByIdInOrderById(final List<Integer> ids);

    /**
     * Retrieves a paginated list of products along with their associated category,
     * using a fetch join to prevent the N+1 select problem.
     *
     * <p>The method loads both product and category data in a single query, improving
     * performance for category-intensive operations.
     *
     * @param pageable pagination information such as page number and size
     * @return a {@link Page} of {@link Product} entities, each with its {@link Category} pre-fetched
     */
    @Query(
            value = "SELECT p FROM Product p JOIN FETCH p.category",
            countQuery = "SELECT count(p) FROM Product p"
    )
    Page<Product> findAllWithCategory(final Pageable pageable);

    /**
     * Retrieves all products whose addition/creation date falls within the
     * specified start and end timestamps.
     *
     * <p>This query is commonly used for analytics, reporting, or date-filtered
     * product searches.
     *
     * @param fromDate the start of the date range (inclusive)
     * @param toDate   the end of the date range (inclusive)
     * @return a list of products created within the specified date range
     */
    Page<Product> findAllByAdditionDateBetween(
            final LocalDateTime fromDate,
            final LocalDateTime toDate,
            final Pageable pageable
    );

    /**
     * Retrieves all products belonging to the given category whose price is
     * greater than or equal to the specified amount.
     *
     * <p>Useful for price-based filtering, threshold-based searches, and building product
     * recommendation logic.
     *
     * @param category the category to filter by; must not be {@code null}
     * @param price    the minimum price threshold
     * @return a list of matching products with price >= given amount
     */
    Page<Product> findAllByCategoryAndPriceGreaterThanEqual(
            final Category category,
            final BigDecimal price,
            final Pageable pageable
    );

    /**
     * Retrieves all products belonging to the given category whose price is
     * less than or equal to the specified amount.
     *
     * <p>Useful for building "under budget" or "at most this price" product lists.
     *
     * @param category the category to filter by; must not be {@code null}
     * @param price    the maximum price threshold
     * @return a list of matching products with price <= given amount
     */
    Page<Product> findAllByCategoryAndPriceLessThanEqual(
            final Category category,
            final BigDecimal price,
            final Pageable pageable
    );
}
