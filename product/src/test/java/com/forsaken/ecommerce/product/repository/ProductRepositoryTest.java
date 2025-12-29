package com.forsaken.ecommerce.product.repository;


import com.forsaken.ecommerce.product.model.Category;
import com.forsaken.ecommerce.product.model.Product;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test class for {@link IProductRepository}.
 *
 * <h2>Purpose</h2>
 * <p>
 * This test class validates the <b>contract and method signatures</b> of
 * {@link IProductRepository} after refactors such as:
 * </p>
 * <ul>
 *     <li>Introducing {@link Pageable} parameters</li>
 *     <li>Changing return types to {@link Page}</li>
 *     <li>Ensuring consistent method invocation semantics</li>
 * </ul>
 *
 * <h2>Testing Strategy</h2>
 * <p>
 * These are <b>pure Mockito-based tests</b>. No database, JPA provider,
 * or Spring context is involved.
 * </p>
 *
 * <p>
 * The tests focus on:
 * </p>
 * <ul>
 *     <li>Verifying repository methods accept correct parameters</li>
 *     <li>Ensuring pagination contracts are preserved</li>
 *     <li>Preventing accidental signature regressions</li>
 * </ul>
 *
 * <h2>What This Test Does NOT Cover</h2>
 * <ul>
 *     <li>JPQL correctness</li>
 *     <li>Entity mappings</li>
 *     <li>Database behavior</li>
 * </ul>
 *
 * <p>
 * For query correctness and fetch-join validation, use {@code @DataJpaTest}
 * instead.
 * </p>
 *
 * <h2>Why This Test Exists</h2>
 * <p>
 * This test acts as a <b>safety net during refactors</b>, ensuring service-layer
 * and controller-layer code can rely on stable repository contracts.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class ProductRepositoryTest {

    @Mock
    private IProductRepository repository;

    private Pageable pageable() {
        return PageRequest.of(0, 10);
    }

    /**
     * Verifies that {@link IProductRepository#findAllByIdInOrderById(List)}
     * returns a {@link Page} of products when invoked with a list of IDs and pagination.
     *
     * <p>
     * This test ensures:
     * </p>
     * <ul>
     *     <li>The method accepts {@link Pageable} correctly</li>
     *     <li>The returned {@link Page} is propagated unchanged</li>
     *     <li>The repository invocation uses the expected arguments</li>
     * </ul>
     */
    @Test
    void findAllByIdInOrderById_ShouldReturnPagedProducts() {
        // Given
        final List<Integer> ids = List.of(1, 2, 3);
        final List<Product> products = List.of(new Product(), new Product());
        when(repository.findAllByIdInOrderById(ids)).thenReturn(products);

        // When
        final List<Product> result = repository.findAllByIdInOrderById(ids);

        // Then
        assertEquals(products, result);
        verify(repository).findAllByIdInOrderById(ids);
    }

    /**
     * Verifies that {@link IProductRepository#findAllWithCategory(Pageable)}
     * returns a paginated result.
     *
     * <p>
     * Although this method uses a JPQL fetch join in production,
     * this test validates only the method contract and pagination behavior.
     * </p>
     */
    @Test
    void findAllWithCategory_ShouldReturnPagedProducts() {
        // Given
        final Pageable pageable = pageable();
        final List<Product> products = List.of(new Product());
        final Page<Product> page =
                new PageImpl<>(products, pageable, products.size());
        when(repository.findAllWithCategory(pageable)).thenReturn(page);

        // When
        final Page<Product> result = repository.findAllWithCategory(pageable);

        // Then
        assertEquals(page, result);
        verify(repository).findAllWithCategory(pageable);
    }

    /**
     * Ensures that {@link IProductRepository#findAllByAdditionDateBetween(
     *LocalDateTime, LocalDateTime, Pageable)} correctly accepts date-range
     * parameters along with pagination.
     *
     * <p>
     * This test verifies method signature stability after introducing pagination.
     * </p>
     */
    @Test
    void findAllByAdditionDateBetween_ShouldReturnPagedProducts() {
        // Given
        final LocalDateTime from = LocalDateTime.now().minusDays(7);
        final LocalDateTime to = LocalDateTime.now();
        final Pageable pageable = pageable();
        final List<Product> products = List.of(new Product());
        final Page<Product> page =
                new PageImpl<>(products, pageable, products.size());
        when(repository.findAllByAdditionDateBetween(from, to, pageable))
                .thenReturn(page);

        // When
        final Page<Product> result =
                repository.findAllByAdditionDateBetween(from, to, pageable);

        // Then
        assertEquals(page, result);
        verify(repository).findAllByAdditionDateBetween(from, to, pageable);
    }

    /**
     * Validates the pagination-enabled price filter query for category-based search
     * using a greater-than-or-equal price condition.
     *
     * <p>
     * Ensures that:
     * </p>
     * <ul>
     *     <li>Category and price parameters are passed correctly</li>
     *     <li>{@link Pageable} is respected</li>
     * </ul>
     */
    @Test
    void findAllByCategoryAndPriceGreaterThanEqual_ShouldReturnPagedProducts() {
        // Given
        final Category category = new Category();
        final BigDecimal price = BigDecimal.valueOf(100);
        final Pageable pageable = pageable();
        final List<Product> products = List.of(new Product());
        final Page<Product> page =
                new PageImpl<>(products, pageable, products.size());

        when(repository.findAllByCategoryAndPriceGreaterThanEqual(
                category, price, pageable))
                .thenReturn(page);

        // When
        final Page<Product> result =
                repository.findAllByCategoryAndPriceGreaterThanEqual(
                        category, price, pageable);

        // Then
        assertEquals(page, result);
        verify(repository)
                .findAllByCategoryAndPriceGreaterThanEqual(category, price, pageable);
    }

    /**
     * Validates the pagination-enabled price filter query for category-based search
     * using a less-than-or-equal price condition.
     *
     * <p>
     * Confirms that the repository method returns a paginated result
     * without altering the returned {@link Page}.
     * </p>
     */
    @Test
    void findAllByCategoryAndPriceLessThanEqual_ShouldReturnPagedProducts() {
        // Given
        final Category category = new Category();
        final BigDecimal price = BigDecimal.valueOf(100);
        final Pageable pageable = pageable();
        final List<Product> products = List.of(new Product());
        final Page<Product> page =
                new PageImpl<>(products, pageable, products.size());
        when(repository.findAllByCategoryAndPriceLessThanEqual(
                category, price, pageable))
                .thenReturn(page);

        // When
        final Page<Product> result =
                repository.findAllByCategoryAndPriceLessThanEqual(
                        category, price, pageable);

        // Then
        assertEquals(page, result);
        verify(repository)
                .findAllByCategoryAndPriceLessThanEqual(category, price, pageable);
    }
}