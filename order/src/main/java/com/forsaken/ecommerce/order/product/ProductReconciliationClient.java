package com.forsaken.ecommerce.order.product;

import com.forsaken.ecommerce.common.responses.ApiResponse;
import com.forsaken.ecommerce.order.configs.client_configurations.product.ProductClientProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.springframework.http.HttpMethod.GET;

/** Read-only client for product reservation reconciliation endpoints. */
@Component
@RequiredArgsConstructor
public class ProductReconciliationClient {

    private static final ParameterizedTypeReference<ApiResponse<ProductReservationReconciliationItem>>
            RESERVATION_RESPONSE = new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<ApiResponse<List<ProductReservationReconciliationItem>>>
            RESERVATIONS_RESPONSE = new ParameterizedTypeReference<>() { };

    private final RestTemplate productRestTemplate;
    private final ProductClientProperties productClientProperties;

    public Optional<ProductReservationReconciliationItem> findByOrderReference(final String orderReference) {
        try {
            final ApiResponse<ProductReservationReconciliationItem> response = productRestTemplate.exchange(
                    productClientProperties.url() + "/reservations/{orderReference}/reconciliation",
                    GET,
                    null,
                    RESERVATION_RESPONSE,
                    orderReference
            ).getBody();
            if (response == null || response.status() != ApiResponse.Status.SUCCESS) {
                throw new ProductLookupException("Invalid reservation reconciliation response", null);
            }
            return Optional.ofNullable(response.data());
        } catch (ProductLookupException ex) {
            throw ex;
        } catch (RestClientException ex) {
            throw new ProductLookupException(
                    "Reservation lookup failed for order reference " + orderReference, ex);
        }
    }

    public List<ProductReservationReconciliationItem> findReservedCreatedBefore(
            final Instant cutoff,
            final String afterReference,
            final int size
    ) {
        try {
            final var uri = UriComponentsBuilder.fromUri(productClientProperties.url())
                    .path("/reservations/reconciliation")
                    .queryParam("olderThan", cutoff)
                    .queryParam("afterReference", afterReference)
                    .queryParam("size", size)
                    .build()
                    .encode()
                    .toUri();
            final ApiResponse<List<ProductReservationReconciliationItem>> response = productRestTemplate.exchange(
                    uri,
                    GET,
                    null,
                    RESERVATIONS_RESPONSE
            ).getBody();
            if (response == null
                    || response.status() != ApiResponse.Status.SUCCESS
                    || response.data() == null) {
                throw new ProductLookupException("Empty reservation reconciliation page", null);
            }
            return response.data();
        } catch (ProductLookupException ex) {
            throw ex;
        } catch (RestClientException ex) {
            throw new ProductLookupException("Reservation reconciliation page lookup failed", ex);
        }
    }
}
