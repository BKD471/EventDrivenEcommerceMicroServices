package com.forsaken.ecommerce.order.payment;

import com.forsaken.ecommerce.common.exceptions.PaymentFailedExceptions;
import com.forsaken.ecommerce.common.responses.ApiResponse;
import com.forsaken.ecommerce.order.customer.CustomerResponse;
import com.forsaken.ecommerce.order.order.model.PaymentMethod;
import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static com.forsaken.ecommerce.common.responses.ApiResponse.Status.SUCCESS;
import static feign.FeignException.BadRequest;
import static feign.FeignException.NotFound;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PaymentServiceImpl}.
 *
 * <p>
 * This test class verifies the behavior of {@link PaymentServiceImpl#pay(PaymentRequest)}
 * in isolation by mocking the downstream {@link IPaymentClient}.
 * </p>
 *
 * <p>
 * <b>Responsibilities covered:</b>
 * </p>
 * <ul>
 *   <li>Successful payment processing.</li>
 *   <li>Defensive handling of null responses and missing payment data.</li>
 *   <li>Translation of Feign client exceptions into
 *       {@link PaymentFailedExceptions}.</li>
 *   <li>Graceful handling of unexpected runtime failures.</li>
 * </ul>
 *
 * <p>
 * <b>Test characteristics:</b>
 * </p>
 * <ul>
 *   <li>Pure unit tests with no Spring context.</li>
 *   <li>Uses Mockito to simulate payment service responses.</li>
 *   <li>Validates exception messages and root causes.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock
    private IPaymentClient paymentClient;

    private PaymentServiceImpl paymentService;

    /**
     * Initializes the {@link PaymentServiceImpl} under test with a mocked
     * {@link IPaymentClient}.
     *
     * <p>
     * A new service instance is created before each test to ensure isolation
     * and avoid shared state between test cases.
     * </p>
     */
    @BeforeEach
    void setUp() {
        paymentService = new PaymentServiceImpl(paymentClient);
    }

    /**
     * Verifies that a successful payment response returns the generated
     * payment identifier.
     */
    @Test
    void shouldReturnPaymentIdWhenPaymentIsSuccessful() throws PaymentFailedExceptions {
        // given
        final PaymentRequest request = constructPaymentRequest();
        final ApiResponse<Integer> response =
                new ApiResponse<>(SUCCESS, 101, null);
        when(paymentClient.requestOrderPayment(request))
                .thenReturn(response);

        // when
        final Integer paymentId = paymentService.pay(request);

        // then
        assertThat(paymentId).isEqualTo(101);
    }

    /**
     * Verifies that a null response from the payment service results in
     * a {@link PaymentFailedExceptions}.
     *
     * <p>
     * This test ensures that null responses are handled defensively and
     * wrapped into a user-friendly exception with the original cause preserved.
     * </p>
     */
    @Test
    void shouldThrowExceptionWhenResponseIsNull() {
        // given
        final PaymentRequest request = constructPaymentRequest();
        when(paymentClient.requestOrderPayment(request))
                .thenReturn(null);

        // when / then
        assertThatThrownBy(() -> paymentService.pay(request))
                .isInstanceOf(PaymentFailedExceptions.class)
                .hasMessageContaining("Unexpected error while processing payment")
                .hasCauseInstanceOf(PaymentFailedExceptions.class)
                .rootCause()
                .hasMessageContaining("Payment Failed");
    }

    /**
     * Verifies that a response containing null payment data results in
     * a {@link PaymentFailedExceptions}.
     */
    @Test
    void shouldThrowExceptionWhenPaymentDataIsNull() {
        // given
        final PaymentRequest request = constructPaymentRequest();
        when(paymentClient.requestOrderPayment(any(PaymentRequest.class)))
                .thenReturn(new ApiResponse<>(null, null, null));

        // when / then
        assertThatThrownBy(() -> paymentService.pay(request))
                .isInstanceOf(PaymentFailedExceptions.class);
    }

    /**
     * Verifies that a Feign {@link feign.FeignException.NotFound} exception
     * is translated into a {@link PaymentFailedExceptions}.
     *
     * <p>
     * This scenario represents a missing or incorrect payment service endpoint.
     * </p>
     */
    @Test
    void shouldThrowExceptionForFeignNotFound() {
        // given
        final PaymentRequest request = constructPaymentRequest();
        final NotFound feign404 = mock(NotFound.class);
        when(feign404.status()).thenReturn(404);
        when(paymentClient.requestOrderPayment(request))
                .thenThrow(feign404);

        // when / then
        assertThatThrownBy(() -> paymentService.pay(request))
                .isInstanceOf(PaymentFailedExceptions.class)
                .hasMessageContaining("endpoint not found")
                .hasCause(feign404);
    }

    /**
     * Verifies that a Feign {@link feign.FeignException.BadRequest} exception
     * is translated into a {@link PaymentFailedExceptions}.
     *
     * <p>
     * This scenario represents an invalid payment request sent to the
     * payment service.
     * </p>
     */
    @Test
    void shouldThrowExceptionForFeignBadRequest() {
        // given
        final PaymentRequest request = constructPaymentRequest();
        final BadRequest badRequest = mock(BadRequest.class);
        when(badRequest.status()).thenReturn(400);
        when(paymentClient.requestOrderPayment(request))
                .thenThrow(badRequest);

        // when / then
        assertThatThrownBy(() -> paymentService.pay(request))
                .isInstanceOf(PaymentFailedExceptions.class)
                .hasMessageContaining("Invalid payment request")
                .hasCause(badRequest);
    }

    /**
     * Verifies that generic {@link FeignException} instances are translated
     * into a {@link PaymentFailedExceptions} indicating a downstream service error.
     */
    @Test
    void shouldThrowExceptionForGenericFeignException() {
        // given
        final PaymentRequest request = constructPaymentRequest();
        final FeignException feignException = mock(FeignException.class);
        when(feignException.status()).thenReturn(502);
        when(paymentClient.requestOrderPayment(request))
                .thenThrow(feignException);

        // when / then
        assertThatThrownBy(() -> paymentService.pay(request))
                .isInstanceOf(PaymentFailedExceptions.class)
                .hasMessageContaining("Payment service error")
                .hasCause(feignException);
    }

    /**
     * Verifies that unexpected runtime exceptions are caught and wrapped into
     * a {@link PaymentFailedExceptions}.
     *
     * <p>
     * This ensures internal failures do not leak raw exceptions to callers.
     * </p>
     */
    @Test
    void shouldThrowExceptionForUnexpectedException() {
        // given
        final PaymentRequest request = constructPaymentRequest();
        when(paymentClient.requestOrderPayment(request))
                .thenThrow(new RuntimeException("boom"));

        // when / then
        assertThatThrownBy(() -> paymentService.pay(request))
                .isInstanceOf(PaymentFailedExceptions.class)
                .hasMessageContaining("Unexpected error")
                .hasCauseInstanceOf(RuntimeException.class);
    }

    /**
     * Constructs a valid {@link PaymentRequest} instance for reuse across tests.
     *
     * <p>
     * The returned request contains all required fields populated with
     * realistic test values.
     * </p>
     *
     * @return a valid {@link PaymentRequest}
     */
    private PaymentRequest constructPaymentRequest() {
        return PaymentRequest.builder()
                .orderId(1)
                .orderReference("ORD-1")
                .amount(BigDecimal.valueOf(500))
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .customer(mock(CustomerResponse.class))
                .build();
    }
}