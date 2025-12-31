package com.forsaken.ecommerce.product.controller;


import com.forsaken.ecommerce.common.exceptions.ProductNotFoundExceptions;
import com.forsaken.ecommerce.common.responses.ApiResponse;
import com.forsaken.ecommerce.common.responses.PagedResponse;
import com.forsaken.ecommerce.product.dto.ProductPurchaseRequest;
import com.forsaken.ecommerce.product.dto.ProductPurchaseResponse;
import com.forsaken.ecommerce.product.dto.ProductRequest;
import com.forsaken.ecommerce.product.dto.ProductResponse;
import com.forsaken.ecommerce.product.exceptions.CategoryNotFoundExceptions;
import com.forsaken.ecommerce.product.service.IProductService;
import com.forsaken.ecommerce.product.service.IS3Service;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static com.forsaken.ecommerce.product.dto.ProductRequest.Direction;

/**
 * Unit tests for {@link ProductControllerImpl}, validating controller-layer behavior
 * and ensuring that it:
 * <ul>
 *     <li>Correctly delegates operations to {@link IProductService}</li>
 *     <li>Correctly delegates to {@link IS3Service} for presigned URL generation</li>
 *     <li>Builds proper {@link ApiResponse} objects</li>
 *     <li>Returns the correct HTTP status codes</li>
 *     <li>Does NOT interact with unrelated services during each endpoint call</li>
 * </ul>
 *
 * <p>All external dependencies are mocked using Mockito to isolate and verify
 * controller behavior only.
 */
@ExtendWith(MockitoExtension.class)
class ProductControllerImplTest {

    @Mock
    private IProductService service;

    @Mock
    private IS3Service s3Service;

    @InjectMocks
    private ProductControllerImpl controller;

    @Mock
    private ProductRequest productRequest;

    @Mock
    private ProductPurchaseRequest productPurchaseRequest;

    @Mock
    private ProductPurchaseResponse productPurchaseResponse;

    /**
     * Verifies that {@link ProductControllerImpl#getPresignedUrl(String, String)}
     * correctly:
     * <ul>
     *     <li>Delegates presigned URL generation to {@link IS3Service}</li>
     *     <li>Returns HTTP 201 (Created)</li>
     *     <li>Wraps the URL map in a successful {@link ApiResponse}</li>
     *     <li>Does not interact with {@link IProductService}</li>
     * </ul>
     */
    @Test
    void getPresignedUrl_ShouldReturnCreatedWithUrlMap() {
        // given
        final String fileName = "myFile.png";
        final String contentType = "image/png";
        final Map<String, String> urlMap = Map.of("url", "https://upload-url");
        when(s3Service.generatePresignedUploadUrl(fileName, contentType))
                .thenReturn(urlMap);

        // when
        final ResponseEntity<ApiResponse<Map<String, String>>> response =
                controller.getPresignedUrl(fileName, contentType);

        // then
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        final var body = response.getBody();
        assertNotNull(body);
        assertEquals(ApiResponse.Status.SUCCESS, body.status());
        assertEquals(urlMap, body.data());
        assertEquals("Presigned Url Generated.", body.message());
        verify(s3Service).generatePresignedUploadUrl(fileName, contentType);
        verifyNoInteractions(service);
    }

    /**
     * Verifies that {@link ProductControllerImpl#createProduct(ProductRequest)}
     * correctly:
     * <ul>
     *     <li>Delegates product creation to {@link IProductService}</li>
     *     <li>Returns HTTP 201 (Created)</li>
     *     <li>Includes the generated product ID in the response body</li>
     *     <li>Does not interact with {@link IS3Service}</li>
     * </ul>
     */
    @Test
    void createProduct_ShouldReturnCreatedWithProductId() throws IOException {
        // given
        final Integer generatedId = 101;
        when(service.createProduct(productRequest)).thenReturn(generatedId);

        // when
        final ResponseEntity<ApiResponse<Integer>> response =
                controller.createProduct(productRequest);

        // then
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        final var body = response.getBody();
        assertNotNull(body);
        assertEquals(ApiResponse.Status.SUCCESS, body.status());
        assertEquals(generatedId, body.data());
        assertEquals("Product Created", body.message());
        verify(service).createProduct(productRequest);
        verifyNoInteractions(s3Service);
    }

    /**
     * Tests that the controller:
     * <ul>
     *     <li>Delegates download URL generation to {@link IS3Service}</li>
     *     <li>Returns HTTP 201 (Created)</li>
     *     <li>Returns a successful {@link ApiResponse} wrapping the URL</li>
     *     <li>Does not interact with {@link IProductService}</li>
     * </ul>
     */
    @Test
    void getDownloadUrl_ShouldReturnCreatedWithStringUrl() {
        // given
        final String key = "products/123.png";
        final String url = "https://download-url";
        when(s3Service.generatePresignedDownloadUrl(key)).thenReturn(url);

        // when
        final ResponseEntity<ApiResponse<String>> response =
                controller.getDownloadUrl(key);

        // then
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        final var body = response.getBody();
        assertNotNull(body);
        assertEquals(url, body.data());
        assertEquals("Presigned Url to download Product Image Generated.", body.message());
        verify(s3Service).generatePresignedDownloadUrl(key);
        verifyNoInteractions(service);
    }

    /**
     * Verifies purchasing flow through controller:
     * <ul>
     *     <li>Delegates to {@link IProductService#purchaseProducts(List)}</li>
     *     <li>Returns HTTP 202 (Accepted)</li>
     *     <li>Returns a list of {@link ProductPurchaseResponse} wrapped in {@link ApiResponse}</li>
     *     <li>Does not interact with {@link IS3Service}</li>
     * </ul>
     *
     * @throws ProductNotFoundExceptions if the service layer indicates missing products
     */
    @Test
    void purchaseProducts_ShouldReturnAcceptedWithPagedResponse() throws ProductNotFoundExceptions {
        // GIVEN
        final List<ProductPurchaseRequest> req =
                List.of(productPurchaseRequest);
        final List<ProductPurchaseResponse> responses =
                List.of(productPurchaseResponse);
        when(service.purchaseProducts(req))
                .thenReturn(responses);

        // WHEN
        final ResponseEntity<ApiResponse<List<ProductPurchaseResponse>>> response =
                controller.purchaseProducts(req);

        // THEN
        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());

        final var body = response.getBody();
        assertNotNull(body);
        assertEquals(ApiResponse.Status.SUCCESS, body.status());
        assertEquals(responses, body.data());
        assertEquals("Product Purchased.", body.message());

        verify(service).purchaseProducts(req);
        verifyNoInteractions(s3Service);
    }

    /**
     * Tests successful retrieval of a product by ID.
     *
     * <p>Ensures that the controller:
     * <ul>
     *     <li>Delegates request to {@link IProductService#getProductById(Integer, boolean)}</li>
     *     <li>Returns HTTP 200 (OK)</li>
     *     <li>Wraps the product response inside {@link ApiResponse}</li>
     *     <li>Does not interact with {@link IS3Service}</li>
     * </ul>
     *
     * @throws ProductNotFoundExceptions if the product does not exist
     */
    @Test
    void findById_ShouldReturnOkWithProductResponse() throws ProductNotFoundExceptions {
        // given
        final Integer productId = 99;
        final boolean signedUrl = true;
        final ProductResponse productResponse = constructProductResponse(productId);
        when(service.getProductById(productId, signedUrl))
                .thenReturn(productResponse);

        // when
        final ResponseEntity<ApiResponse<ProductResponse>> response =
                controller.findById(productId, signedUrl);

        // then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        final var body = response.getBody();
        assertNotNull(body);
        assertEquals(productResponse, body.data());
        assertEquals("Product Found with Id: " + productId, body.message());
        verify(service).getProductById(productId, signedUrl);
        verifyNoInteractions(s3Service);
    }

    /**
     * Verifies that fetching all products:
     * <ul>
     *     <li>Invokes {@link IProductService#getAllProducts(Boolean, Integer, Integer)}</li>
     *     <li>Returns HTTP 200 (OK)</li>
     *     <li>Returns a page of product information</li>
     *     <li>Does not interact with {@link IS3Service}</li>
     * </ul>
     */
    @Test
    void findAll_ShouldReturnOkWithPagedResponse() {
        // given
        final Boolean signedUrl = false;
        final int page = 0, size = 20;
        final PagedResponse<ProductResponse> paged =
                new PagedResponse<>(List.of(), 0, 20, 1,
                        1, null, true);
        when(service.getAllProducts(signedUrl, page, size))
                .thenReturn(paged);

        // when
        final ResponseEntity<ApiResponse<PagedResponse<ProductResponse>>> response =
                controller.findAll(signedUrl, page, size);

        // then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        final var body = response.getBody();
        assertNotNull(body);
        assertEquals(paged, body.data());
        assertEquals("Fetched All Products Information.", body.message());
        verify(service).getAllProducts(signedUrl, page, size);
        verifyNoInteractions(s3Service);
    }

    /**
     * Tests date-range product retrieval.
     *
     * <p>Ensures that:
     * <ul>
     *     <li>Controller calls {@link IProductService#findAllProducts(LocalDateTime, LocalDateTime,
     *     Integer, Integer)}</li>
     *     <li>Responds with HTTP 200 (OK)</li>
     *     <li>Returns a paginated product list</li>
     *     <li>Does not interact with {@link IS3Service}</li>
     * </ul>
     */
    @Test
    void findAllProducts_ShouldReturnOkWithPagedResponse() {
        // given
        final LocalDateTime from = LocalDateTime.now().minusDays(7);
        final LocalDateTime to = LocalDateTime.now();
        final int page = 0, size = 10;
        final PagedResponse<ProductResponse> paged =
                new PagedResponse<>(List.of(), 0, 10, 1,
                        1, null, true);
        when(service.findAllProducts(from, to, page, size))
                .thenReturn(paged);

        // when
        final ResponseEntity<ApiResponse<PagedResponse<ProductResponse>>> response =
                controller.findAllProducts(from, to, page, size);

        // then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        final var body = response.getBody();
        assertNotNull(body);
        assertEquals(paged, body.data());
        assertEquals("Fetched All Products Information created between fromDate to toDate.",
                body.message());
        verify(service).findAllProducts(from, to, page, size);
        verifyNoInteractions(s3Service);
    }

    /**
     * Tests category-based product filtering with price direction.
     *
     * <p>Ensures that:
     * <ul>
     *     <li>Controller delegates to
     *     {@link IProductService#findAllProductsByCategory(Integer, BigDecimal, Direction, Integer, Integer)}</li>
     *     <li>Returns HTTP 200 (OK)</li>
     *     <li>Returns the expected {@link PagedResponse}</li>
     *     <li>Does not interact with {@link IS3Service}</li>
     * </ul>
     *
     * @throws CategoryNotFoundExceptions if the referenced category does not exist
     */
    @Test
    void findAllProductsByCategory_ShouldReturnOkWithPaged() throws CategoryNotFoundExceptions {
        // given
        final int categoryId = 5;
        final BigDecimal price = BigDecimal.valueOf(200);
        final Direction direction = Direction.GE;
        final int page = 0, size = 10;
        final PagedResponse<ProductResponse> paged =
                new PagedResponse<>(List.of(), 0, 10, 1,
                        1, null, true);
        when(service.findAllProductsByCategory(categoryId, price, direction, page, size))
                .thenReturn(paged);

        // when
        final ResponseEntity<ApiResponse<PagedResponse<ProductResponse>>> response =
                controller.findAllProductsByCategory(categoryId, price, direction, page, size);

        // then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        final var body = response.getBody();
        assertNotNull(body);
        assertEquals(paged, body.data());
        assertEquals("Fetched All Products By Category:category.", body.message());
        verify(service).findAllProductsByCategory(categoryId, price, direction, page, size);
        verifyNoInteractions(s3Service);
    }

    /**
     * Utility method for constructing a valid {@link ProductResponse}
     * object used across tests.
     *
     * @param productId the ID assigned to the test product
     * @return a fully populated {@link ProductResponse} instance
     */
    private ProductResponse constructProductResponse(final int productId) {
        return ProductResponse.builder()
                .id(productId)
                .name("Test Product")
                .description("Test Description")
                .availableQuantity(10.0)
                .price(BigDecimal.valueOf(199.99))
                .categoryId(1)
                .categoryName("Category Name")
                .categoryDescription("Category Description")
                .imageUrl("http://image-url")
                .build();
    }
}
