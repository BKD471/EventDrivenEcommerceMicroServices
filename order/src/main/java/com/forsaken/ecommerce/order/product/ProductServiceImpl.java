package com.forsaken.ecommerce.order.product;


import com.forsaken.ecommerce.common.exceptions.BusinessException;
import com.forsaken.ecommerce.common.exceptions.ProductNotFoundExceptions;
import com.forsaken.ecommerce.common.exceptions.ProductServiceException;
import com.forsaken.ecommerce.common.responses.ApiResponse;
import com.forsaken.ecommerce.common.responses.PagedResponse;
import com.forsaken.ecommerce.order.configs.client_configurations.product.ProductClientProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductServiceImpl implements IProductService {

    private final RestTemplate productRestTemplate;
    private final ProductClientProperties productClientProperties;
    private final Class<?> className = ProductServiceImpl.class;

    @Async("appTaskExecutor")
    @Override
    public CompletableFuture<List<PurchaseResponse>> purchaseProducts(final List<PurchaseRequest> requestBody) {
        log.info("Product request received: {}", requestBody);
        final HttpHeaders headers = new HttpHeaders();
        headers.set(CONTENT_TYPE, APPLICATION_JSON_VALUE);

        final HttpEntity<List<PurchaseRequest>> requestEntity = new HttpEntity<>(requestBody, headers);
        final ParameterizedTypeReference<ApiResponse<PagedResponse<PurchaseResponse>>>
                responseType = new ParameterizedTypeReference<>() {
        };
        try {
            final ResponseEntity<ApiResponse<PagedResponse<PurchaseResponse>>> response = productRestTemplate.exchange(
                    productClientProperties.url() + "/purchase",
                    POST,
                    requestEntity,
                    responseType
            );

            if (response.getBody() == null
                    || response.getBody().data() == null
                    || response.getStatusCode().isError()) {
                log.error("Product service returned invalid response, status={}", response.getStatusCode());
                return CompletableFuture.failedFuture(
                        new ProductNotFoundExceptions(
                                "Product service returned invalid response: "
                                        + response.getStatusCode(),
                                "purchaseProducts(List<PurchaseRequest>) in " + className
                        )
                );
            }
            return CompletableFuture.completedFuture(response.getBody().data().content());
        } catch (HttpClientErrorException.NotFound ex) {
            log.error(
                    "Product not found while calling product service. method=purchaseProducts, status={}, responseBody={}",
                    ex.getStatusCode(),
                    ex.getResponseBodyAsString(),
                    ex
            );
            return CompletableFuture.failedFuture(
                    new ProductNotFoundExceptions("Product not found",
                            "purchaseProducts(List<PurchaseRequest>) in " + className,
                            ex)
            );
        } catch (HttpClientErrorException ex) {
            log.error(
                    "Client error while calling product service. method=purchaseProducts, status={}, responseBody={}",
                    ex.getStatusCode(),
                    ex.getResponseBodyAsString(),
                    ex
            );
            return CompletableFuture.failedFuture(
                    new BusinessException("Invalid product request",
                            "purchaseProducts(List<PurchaseRequest>) in " + className,
                            ex)
            );
        } catch (ResourceAccessException ex) {
            log.error("Product service timeout while calling purchaseProducts", ex);
            return CompletableFuture.failedFuture(
                    new ProductServiceException(
                            "Product service timeout",
                            "purchaseProducts(List<PurchaseRequest>) in " + className,
                            ex
                    )
            );
        } catch (Exception ex) {
            log.error("Unexpected error while calling product service", ex);
            return CompletableFuture.failedFuture(
                    new ProductServiceException(
                            "Product service call failed",
                            "purchaseProducts(List<PurchaseRequest>) in " + className,
                            ex
                    )
            );
        }
    }
}