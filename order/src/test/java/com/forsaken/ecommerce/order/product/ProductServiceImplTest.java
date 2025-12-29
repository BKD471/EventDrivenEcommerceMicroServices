package com.forsaken.ecommerce.order.product;

import com.forsaken.ecommerce.common.exceptions.BusinessException;
import com.forsaken.ecommerce.common.exceptions.ProductNotFoundExceptions;
import com.forsaken.ecommerce.common.exceptions.ProductServiceException;
import com.forsaken.ecommerce.common.responses.ApiResponse;
import com.forsaken.ecommerce.common.responses.PagedResponse;
import com.forsaken.ecommerce.order.configs.client_configurations.product.ProductClientProperties;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ProductServiceImpl}.
 *
 * <p>
 * This test class verifies the behavior of
 * {@link ProductServiceImpl#purchaseProducts(List)} in isolation by mocking
 * the underlying {@link RestTemplate} and {@link ProductClientProperties}.
 * </p>
 *
 * <p>
 * <b>Test strategy:</b>
 * </p>
 * <ul>
 *   <li>The <b>success path</b> uses strict argument matching to validate the
 *       HTTP contract (URL, method, headers, and request body).</li>
 *   <li><b>Error scenarios</b> use relaxed matchers to focus on exception
 *       mapping rather than request construction.</li>
 *   <li>The {@code @Async} annotation is not exercised here; the method
 *       executes synchronously in unit tests.</li>
 * </ul>
 *
 * <p>
 * <b>Scenarios covered:</b>
 * </p>
 * <ul>
 *   <li>Successful product purchase.</li>
 *   <li>Null or invalid responses from the product service.</li>
 *   <li>Client-side HTTP errors (4xx).</li>
 *   <li>Timeouts and network failures.</li>
 *   <li>Unexpected runtime failures.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    @Mock
    private RestTemplate productRestTemplate;

    @Mock
    private ProductClientProperties productClientProperties;

    private ProductServiceImpl productService;

    /**
     * Initializes {@link ProductServiceImpl} with mocked dependencies
     * before each test to ensure isolation and repeatability.
     */
    @BeforeEach
    void setUp() {
        productService = new ProductServiceImpl(productRestTemplate, productClientProperties);
    }

    /**
     * Verifies that a successful call to the product service returns the
     * expected list of {@link PurchaseResponse}s.
     *
     * <p>
     * This test intentionally avoids using {@code any()} matchers to strictly
     * validate:
     * </p>
     * <ul>
     *   <li>The constructed request URL.</li>
     *   <li>The HTTP method used.</li>
     *   <li>The request body contents.</li>
     *   <li>The {@code Content-Type} header.</li>
     * </ul>
     */
    @Test
    void shouldReturnPurchaseResponsesWhenCallIsSuccessful() throws Exception {
        // given
        final List<PurchaseRequest> requests = List.of(
                constructPurchaseRequest(1, 2),
                constructPurchaseRequest(2, 1)
        );
        final List<PurchaseResponse> responses = List.of(
                constructPurchaseResponse(1, "Product A", BigDecimal.valueOf(20), 2),
                constructPurchaseResponse(2, "Product B", BigDecimal.valueOf(15), 1)
        );
        final PagedResponse<PurchaseResponse> pagedResponse =
                new PagedResponse<>(responses, 1, responses.size(),
                        responses.size(), 5, Map.of(), false);
        final ApiResponse<PagedResponse<PurchaseResponse>> apiResponse =
                new ApiResponse<>(ApiResponse.Status.SUCCESS, pagedResponse, null);
        final ResponseEntity<ApiResponse<PagedResponse<PurchaseResponse>>> responseEntity =
                ResponseEntity.ok(apiResponse);
        final URI baseUri = URI.create("http://product-service");
        final String expectedUrl = baseUri + "/purchase";
        when(productClientProperties.url()).thenReturn(baseUri);
        final ParameterizedTypeReference<ApiResponse<PagedResponse<PurchaseResponse>>> responseType =
                new ParameterizedTypeReference<>() {
                };
        when(productRestTemplate.exchange(
                eq(expectedUrl),
                eq(HttpMethod.POST),
                argThat(entity ->
                        {
                            Assertions.assertNotNull(entity.getBody());
                            return entity.getBody().equals(requests)
                                    && entity.getHeaders().getContentType() != null
                                    && entity.getHeaders().getContentType().toString()
                                    .equals("application/json");
                        }
                ),
                eq(responseType)
        )).thenReturn(responseEntity);

        // when
        final List<PurchaseResponse> result =
                productService.purchaseProducts(requests).get();

        // then
        assertThat(result).containsExactlyElementsOf(responses);
    }

    /**
     * Verifies that the service fails with {@link ProductNotFoundExceptions}
     * when the product service returns a null response body.
     */
    @Test
    void shouldFailWhenResponseBodyIsNull() {
        // given
        when(productClientProperties.url()).thenReturn(URI.create("http://product-service"));
        when(productRestTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)
        )).thenReturn(ResponseEntity.ok(null));

        // when
        final CompletableFuture<List<PurchaseResponse>> future =
                productService.purchaseProducts(List.of());

        // then
        assertThatThrownBy(future::join)
                .hasCauseInstanceOf(ProductNotFoundExceptions.class)
                .hasMessageContaining("invalid response");
    }

    /**
     * Verifies that a {@link HttpClientErrorException.NotFound} thrown by the
     * product service is translated into a {@link ProductNotFoundExceptions}.
     */
    @Test
    void shouldFailWhenProductNotFound() {
        // given
        when(productClientProperties.url()).thenReturn(URI.create("http://product-service"));
        final HttpClientErrorException.NotFound notFound =
                (HttpClientErrorException.NotFound) HttpClientErrorException.create(
                        HttpStatus.NOT_FOUND,
                        "Not Found",
                        HttpHeaders.EMPTY,
                        null,
                        null
                );
        when(productRestTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)
        )).thenThrow(notFound);

        // when
        final CompletableFuture<List<PurchaseResponse>> future =
                productService.purchaseProducts(List.of());

        // then
        assertThatThrownBy(future::join)
                .hasCauseInstanceOf(ProductNotFoundExceptions.class)
                .hasMessageContaining("Product not found");
    }

    /**
     * Verifies that client-side HTTP errors (4xx), other than 404, are mapped
     * to a {@link BusinessException}.
     */
    @Test
    void shouldFailForClientErrors() {
        // given
        when(productClientProperties.url()).thenReturn(URI.create("http://product-service"));
        final HttpClientErrorException badRequest =
                HttpClientErrorException.create(
                        HttpStatus.BAD_REQUEST,
                        "Bad Request",
                        HttpHeaders.EMPTY,
                        null,
                        null
                );
        when(productRestTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)
        )).thenThrow(badRequest);

        // when
        final CompletableFuture<List<PurchaseResponse>> future =
                productService.purchaseProducts(List.of());

        // then
        assertThatThrownBy(future::join)
                .hasCauseInstanceOf(BusinessException.class)
                .hasMessageContaining("Invalid product request");
    }

    /**
     * Verifies that timeouts or network failures are translated into a
     * {@link ProductServiceException}.
     */
    @Test
    void shouldFailOnTimeout() {
        // given
        when(productClientProperties.url()).thenReturn(URI.create("http://product-service"));
        when(productRestTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)
        )).thenThrow(new ResourceAccessException("timeout"));

        // when
        final CompletableFuture<List<PurchaseResponse>> future =
                productService.purchaseProducts(List.of());

        // then
        assertThatThrownBy(future::join)
                .hasCauseInstanceOf(ProductServiceException.class)
                .hasMessageContaining("timeout");
    }

    /**
     * Verifies that unexpected runtime exceptions are caught and wrapped into
     * a {@link ProductServiceException}.
     */
    @Test
    void shouldFailForUnexpectedException() {
        // given
        when(productClientProperties.url()).thenReturn(URI.create("http://product-service"));
        when(productRestTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)
        )).thenThrow(new RuntimeException("boom"));

        // when
        final CompletableFuture<List<PurchaseResponse>> future =
                productService.purchaseProducts(List.of());

        // then
        assertThatThrownBy(future::join)
                .hasCauseInstanceOf(ProductServiceException.class)
                .hasMessageContaining("Product service call failed");
    }

    /**
     * Creates a valid {@link PurchaseResponse} instance for use in tests.
     */
    private PurchaseResponse constructPurchaseResponse(
            final Integer productId,
            final String name,
            final BigDecimal price,
            final double quantity
    ) {
        return PurchaseResponse.builder()
                .productId(productId)
                .name(name)
                .description("Test PRODUCT")
                .price(price)
                .quantity(quantity)
                .build();
    }

    /**
     * Creates a valid {@link PurchaseRequest} instance for use in tests.
     */
    private PurchaseRequest constructPurchaseRequest(
            final Integer productId,
            final double quantity
    ) {
        return new PurchaseRequest(productId, quantity);
    }
}