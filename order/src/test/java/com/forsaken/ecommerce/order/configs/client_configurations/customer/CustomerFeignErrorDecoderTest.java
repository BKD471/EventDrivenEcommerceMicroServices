package com.forsaken.ecommerce.order.configs.client_configurations.customer;

import com.forsaken.ecommerce.common.exceptions.BusinessException;
import com.forsaken.ecommerce.common.exceptions.CustomerNotFoundExceptions;
import feign.Request;
import feign.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link CustomerFeignErrorDecoder}.
 *
 * <p>
 * This test class verifies the HTTP status code to exception mapping logic
 * implemented by the Feign {@link feign.codec.ErrorDecoder} used for
 * customer service interactions.
 * </p>
 *
 * <p>
 * <b>Purpose:</b>
 * </p>
 * <ul>
 *   <li>Ensure customer-related HTTP errors are translated into
 *       meaningful, domain-specific exceptions.</li>
 *   <li>Validate correct differentiation between client errors,
 *       resource-not-found scenarios, timeouts, and server failures.</li>
 *   <li>Confirm fallback behavior for unhandled HTTP status codes.</li>
 * </ul>
 *
 * <p>
 * <b>Test scope:</b>
 * </p>
 * <ul>
 *   <li>Pure unit tests with no Spring context.</li>
 *   <li>No real HTTP or Feign client calls.</li>
 *   <li>No verification of logging behavior.</li>
 * </ul>
 */
class CustomerFeignErrorDecoderTest {

    private CustomerFeignErrorDecoder decoder;

    /**
     * Initializes a fresh {@link CustomerFeignErrorDecoder} instance
     * before each test execution.
     *
     * <p>
     * A new instance per test ensures isolation and prevents side effects
     * across test cases.
     * </p>
     */
    @BeforeEach
    void setUp() {
        decoder = new CustomerFeignErrorDecoder();
    }

    /**
     * Verifies that an HTTP 400 (Bad Request) response is translated into
     * a {@link BusinessException}.
     *
     * <p>
     * This scenario represents client-side validation errors where the
     * customer request is malformed or invalid.
     * </p>
     */
    @Test
    void shouldThrowBusinessExceptionFor400() {
        // given
        final Response response = buildResponse(400);

        // when
        final Exception ex = decoder.decode("findCustomer()", response);

        // then
        assertThat(ex)
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Invalid customer request");
    }

    /**
     * Verifies that an HTTP 404 (Not Found) response is translated into
     * a {@link CustomerNotFoundExceptions}.
     *
     * <p>
     * This scenario represents a valid request for a customer resource
     * that does not exist.
     * </p>
     */
    @Test
    void shouldThrowCustomerNotFoundExceptionFor404() {
        // given
        final Response response = buildResponse(404);

        // when
        final Exception ex = decoder.decode("findCustomer()", response);

        // then
        assertThat(ex)
                .isInstanceOf(CustomerNotFoundExceptions.class)
                .hasMessageContaining("No customer exists with the provided ID");
    }

    /**
     * Verifies that timeout-related HTTP responses are translated into
     * a {@link BusinessException}.
     *
     * <p>
     * This scenario indicates that the customer service did not respond
     * within the expected time window.
     * </p>
     */
    @Test
    void shouldThrowBusinessExceptionForTimeouts() {
        // given
        final Response response = buildResponse(504);

        // when
        final Exception ex = decoder.decode("findCustomer()", response);

        // then
        assertThat(ex)
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("timeout");
    }

    /**
     * Verifies that server-side HTTP errors are translated into
     * a {@link BusinessException}.
     *
     * <p>
     * This scenario represents customer service outages or internal
     * server failures.
     * </p>
     */
    @Test
    void shouldThrowBusinessExceptionForServerErrors() {
        // given
        final Response response = buildResponse(503);

        // when
        final Exception ex = decoder.decode("findCustomer()", response);

        // then
        assertThat(ex)
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("unavailable");
    }

    /**
     * Verifies that unhandled HTTP status codes are delegated to Feign’s
     * default {@link feign.codec.ErrorDecoder}.
     *
     * <p>
     * This ensures forward compatibility for unexpected or newly introduced
     * HTTP status codes.
     * </p>
     */
    @Test
    void shouldDelegateToDefaultDecoderForUnknownStatus() {
        // given
        final Response response = buildResponse(418); // I'm a teapot ☕

        // when
        final Exception ex = decoder.decode("findCustomer()", response);

        // then
        assertThat(ex).isNotNull();
    }

    /**
     * Builds a minimal Feign {@link Response} with the given HTTP status.
     *
     * <p>
     * This helper method allows tests to simulate HTTP error responses
     * without performing real network calls.
     * </p>
     *
     * @param status the HTTP status code to simulate
     * @return a {@link Response} instance with the specified status
     */
    private Response buildResponse(final int status) {
        return Response.builder()
                .status(status)
                .reason("error")
                .request(
                        Request.create(
                                Request.HttpMethod.GET,
                                "/customers/123",
                                Collections.emptyMap(),
                                null,
                                StandardCharsets.UTF_8,
                                null
                        )
                )
                .build();
    }
}

