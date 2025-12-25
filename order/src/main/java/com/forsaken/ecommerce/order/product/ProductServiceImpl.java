package com.forsaken.ecommerce.order.product;


import com.forsaken.ecommerce.common.exceptions.BusinessException;
import com.forsaken.ecommerce.common.exceptions.ProductNotFoundExceptions;
import com.forsaken.ecommerce.common.responses.ApiResponse;
import com.forsaken.ecommerce.common.responses.PagedResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
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

    @Value("${application.config.product-url}")
    private String productUrl;
    private final RestTemplate restTemplate;
    private final Class<?> className = ProductServiceImpl.class;

    @Async("appTaskExecutor")
    @Override
    public CompletableFuture<List<PurchaseResponse>> purchaseProducts(final List<PurchaseRequest> requestBody) throws BusinessException, ProductNotFoundExceptions {
        log.info("Product request received: {}", requestBody);
        final HttpHeaders headers = new HttpHeaders();
        headers.set(CONTENT_TYPE, APPLICATION_JSON_VALUE);

        final HttpEntity<List<PurchaseRequest>> requestEntity = new HttpEntity<>(requestBody, headers);

        final ParameterizedTypeReference<ApiResponse<PagedResponse<PurchaseResponse>>>
                responseType = new ParameterizedTypeReference<>() {
        };
        final ResponseEntity<ApiResponse<PagedResponse<PurchaseResponse>>> response = restTemplate.exchange(
                productUrl + "/purchase",
                POST,
                requestEntity,
                responseType
        );

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null
                || response.getBody().data() == null || response.getStatusCode().isError()) {
            log.error("Product request failed: {}", response.getBody());
            throw new ProductNotFoundExceptions(
                    "An error occurred while processing the products purchase: " + response.getStatusCode(),
                    "purchaseProducts(final List<PurchaseRequest> requestBody) in " + className
            );
        }
        return CompletableFuture.completedFuture(response.getBody().data().content());
    }
}
