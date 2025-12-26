package com.forsaken.ecommerce.order.product;


import com.forsaken.ecommerce.common.exceptions.ProductNotFoundExceptions;
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
        } catch (Exception ex) {
            log.error("Product service call failed", ex);
            return CompletableFuture.failedFuture(
                    new ProductNotFoundExceptions(
                            "Failed to call product service",
                            "purchaseProducts(List<PurchaseRequest>) in " + className,
                            ex
                    )
            );
        }
    }
}