package com.forsaken.ecommerce.product.order;

import com.forsaken.ecommerce.common.responses.ApiResponse;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Optional;

@Service
public class OrderStatusClientImpl implements IOrderStatusClient {

    private final RestTemplate restTemplate;
    private final ReservationOrderClientProperties properties;

    public OrderStatusClientImpl(
            final RestTemplateBuilder builder,
            final ReservationOrderClientProperties properties
    ) {
        this.properties = properties;
        this.restTemplate = builder
                .setConnectTimeout(properties.connectTimeout())
                .setReadTimeout(properties.readTimeout())
                .build();
    }

    @Override
    public Optional<String> findSagaStatus(final String orderReference) {
        final java.net.URI uri = UriComponentsBuilder.fromUri(properties.url())
                .pathSegment("reference", orderReference, "saga-status")
                .build()
                .encode()
                .toUri();
        try {
            final ResponseEntity<ApiResponse<String>> response = restTemplate.exchange(
                    uri, HttpMethod.GET, null, new ParameterizedTypeReference<ApiResponse<String>>() { });
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new OrderStatusLookupException(
                        "Unexpected order service response for reference=" + orderReference);
            }
            return Optional.ofNullable(response.getBody().data());
        } catch (RestClientException ex) {
            throw new OrderStatusLookupException(
                    "Unable to look up order status for reference=" + orderReference, ex);
        }
    }
}
