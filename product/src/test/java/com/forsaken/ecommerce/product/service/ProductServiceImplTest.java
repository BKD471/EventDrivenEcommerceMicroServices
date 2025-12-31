package com.forsaken.ecommerce.product.service;


import com.forsaken.ecommerce.common.exceptions.ProductNotFoundExceptions;
import com.forsaken.ecommerce.common.responses.PagedResponse;
import com.forsaken.ecommerce.product.configs.general.ProductProperties;
import com.forsaken.ecommerce.product.dto.ProductPurchaseRequest;
import com.forsaken.ecommerce.product.dto.ProductPurchaseResponse;
import com.forsaken.ecommerce.product.dto.ProductRequest;
import com.forsaken.ecommerce.product.dto.ProductResponse;
import com.forsaken.ecommerce.product.exceptions.CategoryNotFoundExceptions;
import com.forsaken.ecommerce.product.model.Category;
import com.forsaken.ecommerce.product.model.Product;
import com.forsaken.ecommerce.product.repository.ICategoryRepository;
import com.forsaken.ecommerce.product.repository.IProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static com.forsaken.ecommerce.product.dto.ProductRequest.Direction;

/**
 * Unit tests for {@link ProductServiceImpl}, validating the service-layer business logic
 * for product creation, retrieval, category filtering, date filtering, S3 presigned URL
 * enrichment, and transactional product purchasing.
 *
 * <p>All external dependencies such as repositories and S3 services are mocked using Mockito
 * to isolate pure service logic and verify:
 * <ul>
 *     <li>Correct delegation to repositories</li>
 *     <li>Correct exception throwing for invalid scenarios</li>
 *     <li>Accurate business transformations and updates</li>
 *     <li>Proper stock validation during purchases</li>
 *     <li>S3 URL enrichment when enabled</li>
 * </ul>
 *
 * <p>This class ensures high reliability of the core product management behavior.
 */
@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    @Mock
    private IProductRepository productRepository;

    @Mock
    private ICategoryRepository categoryRepository;

    @Mock
    private ProductProperties productProperties;

    @Mock
    private IS3Service s3Service;

    @InjectMocks
    private ProductServiceImpl service;


    /**
     * Tests that a new product is successfully created.
     *
     * <p>Ensures that:
     * <ul>
     *     <li>The request is converted to a {@link Product} entity</li>
     *     <li>The product is saved through the repository</li>
     *     <li>The returned ID matches the saved entity</li>
     * </ul>
     */
    @Test
    void createProduct_ShouldSaveAndReturnId() {
        // given
        final ProductRequest request = mock(ProductRequest.class);
        final Product product = constructProduct();
        when(request.toProduct()).thenReturn(product);
        when(productRepository.save(product)).thenReturn(product);

        // when
        final Integer id = service.createProduct(request);

        // then
        assertEquals(1, id);
        verify(productRepository).save(product);
    }

    /**
     * Verifies that when signed URLs are enabled, the service:
     * <ul>
     *     <li>Fetches paginated products with categories</li>
     *     <li>Generates presigned download URLs for product images</li>
     *     <li>Mutates the product entity to contain the signed URL</li>
     *     <li>Returns a {@link PagedResponse} containing the enriched DTOs</li>
     * </ul>
     */
    @Test
    void getAllProducts_ShouldReturnSignedUrls_WhenEnabled() {
        // given
        final Pageable pageable = PageRequest.of(0, 10);
        final Product product = constructProduct();
        final Page<Product> page =
                new PageImpl<>(List.of(product), pageable, 1);
        when(productProperties.maxPageSize()).thenReturn(50);
        when(productRepository.findAllWithCategory(pageable))
                .thenReturn(page);
        when(s3Service.generatePresignedDownloadUrl("image-key"))
                .thenReturn("signed-url");

        // when
        final PagedResponse<ProductResponse> response =
                service.getAllProducts(true, 1, 10);

        // then
        assertEquals(1, response.totalElements());
        assertEquals("signed-url", product.getImageUrl());
        verify(s3Service).generatePresignedDownloadUrl("image-key");
    }

    /**
     * Ensures that the service returns a full {@link ProductResponse} for a valid product ID.
     *
     * <p>Checks that:
     * <ul>
     *     <li>The product is retrieved correctly from the repository</li>
     *     <li>A signed image URL is generated when requested</li>
     *     <li>Entity fields are correctly mapped into the DTO</li>
     * </ul>
     *
     * @throws ProductNotFoundExceptions if the product is missing (not expected here)
     */
    @Test
    void getProductById_ShouldReturnResponse_WhenProductExists() throws ProductNotFoundExceptions {
        // given
        final Product product = constructProduct();
        when(productRepository.findById(1)).thenReturn(Optional.of(product));
        when(s3Service.generatePresignedDownloadUrl("image-key"))
                .thenReturn("signed-img");

        // when
        final ProductResponse response = service.getProductById(1, true);

        // then
        assertEquals(1, response.id());
        assertEquals("signed-img", product.getImageUrl());
    }

    /**
     * Ensures that the service throws {@link ProductNotFoundExceptions}
     * when attempting to fetch a non-existent product.
     */
    @Test
    void getProductById_ShouldThrow_WhenNotFound() {
        // given
        when(productRepository.findById(1)).thenReturn(Optional.empty());

        // when / then
        assertThrows(ProductNotFoundExceptions.class,
                () -> service.getProductById(1, false));
    }

    /**
     * Ensures that the purchase operation fails when the number of retrieved products
     * does not match the number of requested product IDs.
     *
     * <p>This validates the integrity rule: every requested product must exist.
     */
    @Test
    void purchaseProducts_ShouldThrow_WhenMismatchInIds() {
        // GIVEN
        final ProductPurchaseRequest req =
                new ProductPurchaseRequest(1, 1);

        when(productRepository.findAllByIdInOrderById(List.of(1)))
                .thenReturn(List.of()); // simulate missing product

        // WHEN / THEN
        assertThrows(ProductNotFoundExceptions.class,
                () -> service.purchaseProducts(List.of(req)));
    }

    /**
     * Ensures that the service throws {@link ProductNotFoundExceptions} when
     * attempting to purchase a quantity greater than the available stock.
     *
     * <p>Validates the business rule enforcing stock sufficiency.
     */
    @Test
    void purchaseProducts_ShouldThrow_WhenStockInsufficient() {
        // GIVEN
        final Product product = constructProduct();
        product.setAvailableQuantity(2); // less than requested

        final ProductPurchaseRequest req =
                new ProductPurchaseRequest(1, 5);

        when(productRepository.findAllByIdInOrderById(List.of(1)))
                .thenReturn(List.of(product));

        // WHEN / THEN
        assertThrows(ProductNotFoundExceptions.class,
                () -> service.purchaseProducts(List.of(req)));
    }

    /**
     * Verifies that a successful product purchase:
     * <ul>
     *     <li>Loads all required products from the repository</li>
     *     <li>Validates sufficient available stock</li>
     *     <li>Updates inventory quantities correctly</li>
     *     <li>Persists the updated product state</li>
     *     <li>Returns a list of {@link ProductPurchaseResponse} with purchase details</li>
     * </ul>
     */
    @Test
    void purchaseProducts_ShouldReturnResponse_WhenSuccessful() throws ProductNotFoundExceptions {
        // GIVEN
        final Product product = constructProduct();
        product.setId(1);
        product.setAvailableQuantity(5);

        final ProductPurchaseRequest request =
                new ProductPurchaseRequest(1, 2);
        when(productRepository.findAllByIdInOrderById(List.of(1)))
                .thenReturn(List.of(product));
        when(productRepository.save(product))
                .thenReturn(product);

        // WHEN
        final List<ProductPurchaseResponse> responses =
                service.purchaseProducts(List.of(request));

        // THEN
        assertNotNull(responses);
        assertEquals(1, responses.size());

        final ProductPurchaseResponse purchaseResponse = responses.get(0);
        assertEquals(1, purchaseResponse.productId());
        assertEquals(2, purchaseResponse.quantity());

        // stock reduced correctly
        assertEquals(3, product.getAvailableQuantity());
        verify(productRepository).findAllByIdInOrderById(List.of(1));
        verify(productRepository).save(product);
    }

    /**
     * Ensures correct filtering of products using a creation-date range.
     *
     * <p>Verifies:
     * <ul>
     *     <li>Repository receives the exact from/to timestamps</li>
     *     <li>The resulting {@link PagedResponse} contains the correct values</li>
     * </ul>
     */
    @Test
    void findAllProducts_ShouldReturnPagedResponse() {
        // GIVEN
        final Product product = constructProduct();
        final LocalDateTime from = LocalDateTime.now().minusDays(1);
        final LocalDateTime to = LocalDateTime.now();

        final Pageable pageable = PageRequest.of(0, 10);
        final Page<Product> page =
                new PageImpl<>(List.of(product), pageable, 1);
        when(productProperties.maxPageSize()).thenReturn(50);
        when(productRepository.findAllByAdditionDateBetween(from, to, pageable))
                .thenReturn(page);

        // WHEN
        final PagedResponse<ProductResponse> response =
                service.findAllProducts(from, to, 1, 10);

        // THEN
        assertEquals(1, response.totalElements());
    }

    /**
     * Ensures that category-based filtering throws {@link CategoryNotFoundExceptions}
     * when the requested category ID does not exist.
     */
    @Test
    void findAllProductsByCategory_ShouldThrow_WhenCategoryMissing() {
        // GIVEN
        when(productProperties.maxPageSize()).thenReturn(50);
        when(categoryRepository.findById(1)).thenReturn(Optional.empty());

        // WHEN / THEN
        assertThrows(CategoryNotFoundExceptions.class,
                () -> service.findAllProductsByCategory(
                        1, BigDecimal.TEN, Direction.GE, 1, 10));
    }

    /**
     * Tests price-based filtering for category search using {@code Direction.GE}
     * (greater-than-or-equal).
     *
     * <p>Ensures:
     * <ul>
     *     <li>Category lookup succeeds</li>
     *     <li>Repository is queried with the correct parameters</li>
     *     <li>The DTO conversion is correct</li>
     *     <li>The paginated response contains accurate mapping</li>
     * </ul>
     */
    @Test
    void findAllProductsByCategory_ShouldReturnPaged_GreaterThanEqual() throws CategoryNotFoundExceptions {
        // GIVEN
        final Category category = constructCategory();
        final Product product = constructProduct();

        final Pageable pageable = PageRequest.of(0, 10);
        final Page<Product> page =
                new PageImpl<>(List.of(product), pageable, 1);

        when(productProperties.maxPageSize()).thenReturn(50);
        when(categoryRepository.findById(1))
                .thenReturn(Optional.of(category));
        when(productRepository.findAllByCategoryAndPriceGreaterThanEqual(
                category, BigDecimal.TEN, pageable))
                .thenReturn(page);

        // WHEN
        final PagedResponse<ProductResponse> response =
                service.findAllProductsByCategory(
                        1, BigDecimal.TEN, Direction.GE, 1, 10);

        // THEN
        assertEquals(1, response.totalElements());
    }

    /**
     * Tests price-based filtering for category search using {@code Direction.LE}
     * (less-than-or-equal).
     *
     * <p>Ensures that repository results are correctly wrapped in a
     * {@link PagedResponse}.
     */
    @Test
    void findAllProductsByCategory_ShouldReturnPaged_LessThanEqual() throws CategoryNotFoundExceptions {
        // GIVEN
        final Category category = constructCategory();
        final Product product = constructProduct();
        final Pageable pageable = PageRequest.of(0, 10);
        final Page<Product> page =
                new PageImpl<>(List.of(product), pageable, 1);
        when(productProperties.maxPageSize()).thenReturn(50);
        when(categoryRepository.findById(1))
                .thenReturn(Optional.of(category));
        when(productRepository.findAllByCategoryAndPriceLessThanEqual(
                category, BigDecimal.TEN, pageable))
                .thenReturn(page);

        // WHEN
        final PagedResponse<ProductResponse> response =
                service.findAllProductsByCategory(
                        1, BigDecimal.TEN, Direction.LE, 1, 10);

        // THEN
        assertEquals(1, response.totalElements());
    }

    /**
     * Utility method for constructing a fully populated {@link Product} entity
     * used across test cases.
     *
     * @return a preconfigured {@link Product} instance with ID, name, category,
     * available quantity, price, and image key
     */
    private Product constructProduct() {
        return Product.builder()
                .id(1)
                .name("Test Product")
                .description("Test description")
                .price(BigDecimal.TEN)
                .category(constructCategory())
                .availableQuantity(20)
                .imageUrl("image-key")
                .build();
    }

    /**
     * Utility method for creating a simple {@link Category} entity used in tests.
     *
     * @return a {@link Category} with a predefined ID and name
     */
    private Category constructCategory() {
        return Category.builder()
                .id(1)
                .name("Test Category")
                .build();
    }
}
