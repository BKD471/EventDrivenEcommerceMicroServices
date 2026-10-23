package com.forsaken.ecommerce.payment.invoice;

import com.forsaken.ecommerce.payment.batch.InvoiceBatchProperties;
import com.forsaken.ecommerce.payment.model.Payment;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Map;

@Service
public class InvoiceClientImpl implements IInvoiceClient {

    private static final String TOKEN_HEADER = "X-Internal-Token";

    private final RestTemplate restTemplate;
    private final InvoiceBatchProperties properties;

    public InvoiceClientImpl(final RestTemplateBuilder builder, final InvoiceBatchProperties properties) {
        this.properties = properties;
        this.restTemplate = builder
                .setConnectTimeout(properties.connectTimeout())
                .setReadTimeout(properties.readTimeout())
                .build();
    }

    @Override
    public boolean exists(final String orderReference) {
        final Map<String, Boolean> body = call(uri(orderReference, null), HttpMethod.GET, null, orderReference);
        return Boolean.TRUE.equals(body.get("exists"));
    }

    @Override
    public void regenerate(final Payment payment) {
        final String reference = payment.getOrderReference();
        final Map<String, Object> request = Map.of(
                "customerName", payment.getCustomerFirstname() + " " + payment.getCustomerLastname(),
                "customerEmail", payment.getCustomerEmail(),
                "amount", payment.getAmount(),
                "paymentMethod", payment.getPaymentMethod().name(),
                "paymentDate", payment.getCreatedDate().toString());
        call(uri(reference, "regenerate"), HttpMethod.POST, request, reference);
    }

    private <T> Map<String, Boolean> call(
            final URI uri, final HttpMethod method, final T requestBody, final String reference
    ) {
        final HttpHeaders headers = new HttpHeaders();
        headers.set(TOKEN_HEADER, properties.token());
        try {
            final ResponseEntity<Map<String, Boolean>> response = restTemplate.exchange(
                    uri, method, new HttpEntity<>(requestBody, headers),
                    new ParameterizedTypeReference<Map<String, Boolean>>() { });
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new InvoiceClientException("Unexpected notification response for reference=" + reference);
            }
            return response.getBody();
        } catch (RestClientException ex) {
            throw new InvoiceClientException("Invoice call failed for reference=" + reference, ex);
        }
    }

    private URI uri(final String orderReference, final String suffix) {
        final UriComponentsBuilder builder = UriComponentsBuilder.fromUri(properties.notificationUrl())
                .pathSegment(orderReference);
        if (suffix != null) {
            builder.pathSegment(suffix);
        }
        return builder.build().encode().toUri();
    }
}
