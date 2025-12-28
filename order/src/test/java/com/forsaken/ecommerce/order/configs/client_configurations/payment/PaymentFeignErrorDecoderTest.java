package com.forsaken.ecommerce.order.configs.client_configurations.payment;

import com.forsaken.ecommerce.common.exceptions.BusinessException;
import com.forsaken.ecommerce.common.exceptions.PaymentFailedExceptions;
import feign.Request;
import feign.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link PaymentFeignErrorDecoder}.
 *
 * <p>
 * This test class verifies the HTTP status to exception mapping logic implemented
 * by the Feign {@link feign.codec.ErrorDecoder} used for payment service calls.
 * </p>
 *
 * <p>
 * <b>Scope of testing:</b>
 * </p>
 * <ul>
 *   <li>Validates that specific HTTP response status codes are translated into
 *       domain-specific exceptions.</li>
 *   <li>Ensures business and infrastructure failures are differentiated correctly.</li>
 *   <li>Confirms fallback behavior delegates to Feign’s default error decoder
 *       for unhandled status codes.</li>
 * </ul>
 *
 * <p>
 * <b>Out of scope:</b>
 * </p>
 * <ul>
 *   <li>Actual Feign client execution.</li>
 *   <li>Network or HTTP behavior.</li>
 *   <li>Logging verification.</li>
 * </ul>
 *
 * <p>
 * These tests are pure unit tests and do not require a Spring context.
 * </p>
 */
class PaymentFeignErrorDecoderTest {

    private PaymentFeignErrorDecoder decoder;

    /**
     * Initializes the {@link PaymentFeignErrorDecoder} before each test execution.
     *
     * <p>
     * A fresh decoder instance is created for every test to ensure isolation and
     * prevent state leakage across test cases.
     * </p>
     */
    @BeforeEach
    void setUp() {
        decoder = new PaymentFeignErrorDecoder();
    }

    /**
     * Verifies that an HTTP 400 (Bad Request) response is translated into
     * a {@link BusinessException}.
     *
     * <p>
     * This scenario represents client-side validation failures where the
     * payment request itself is invalid.
     * </p>
     */
    @Test
    void shouldThrowBusinessExceptionFor400() {
        // given
        final Response response = buildResponse(400);

        // when
        final Exception ex = decoder.decode("pay()", response);

        // then
        assertThat(ex)
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Invalid payment request");
    }

    /**
     * Verifies that an HTTP 402 (Payment Required) response is translated into
     * a {@link PaymentFailedExceptions}.
     *
     * <p>
     * This scenario represents a payment provider decline or insufficient funds
     * situation.
     * </p>
     */
    @Test
    void shouldThrowPaymentFailedExceptionFor402() {
        // given
        final Response response = buildResponse(402);

        // when
        final Exception ex = decoder.decode("pay()", response);

        // then
        assertThat(ex)
                .isInstanceOf(PaymentFailedExceptions.class)
                .hasMessageContaining("Payment was declined");
    }

    /**
     * Verifies that timeout-related HTTP responses are translated into
     * a {@link PaymentFailedExceptions}.
     *
     * <p>
     * This test covers gateway or upstream service timeout scenarios, indicating
     * that the payment service did not respond in a timely manner.
     * </p>
     */
    @Test
    void shouldThrowPaymentFailedExceptionForTimeouts() {
        // given
        final Response response = buildResponse(504);

        // when
        final Exception ex = decoder.decode("pay()", response);

        // then
        assertThat(ex)
                .isInstanceOf(PaymentFailedExceptions.class)
                .hasMessageContaining("timeout");
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
        final Exception ex = decoder.decode("pay()", response);

        // then
        assertThat(ex).isNotNull();
    }

    /**
     * Builds a minimal Feign {@link Response} instance with the given HTTP status.
     *
     * <p>
     * This helper method avoids the need for real HTTP calls while allowing
     * precise control over the response status used in each test.
     * </p>
     *
     * @param status HTTP status code to simulate
     * @return a {@link Response} instance with the specified status
     */
    private Response buildResponse(final int status) {
        return Response.builder()
                .status(status)
                .reason("error")
                .request(
                        Request.create(
                                Request.HttpMethod.POST,
                                "/payments",
                                Collections.emptyMap(),
                                null,
                                StandardCharsets.UTF_8,
                                null
                        )
                )
                .build();
    }
}
