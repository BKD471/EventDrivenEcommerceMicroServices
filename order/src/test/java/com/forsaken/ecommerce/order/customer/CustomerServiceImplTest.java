package com.forsaken.ecommerce.order.customer;

import com.forsaken.ecommerce.common.exceptions.CustomerNotFoundExceptions;
import com.forsaken.ecommerce.common.responses.ApiResponse;
import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;

import static com.forsaken.ecommerce.common.responses.ApiResponse.Status.FAILED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static feign.FeignException.NotFound;

/**
 * Unit tests for {@link CustomerServiceImpl}.
 *
 * <p>
 * This test class verifies the behavior of {@link CustomerServiceImpl#getCustomer(String)}
 * in isolation by mocking the downstream {@link ICustomerClient}.
 * </p>
 *
 * <p>
 * <b>Responsibilities covered:</b>
 * </p>
 * <ul>
 *   <li>Successful customer retrieval.</li>
 *   <li>Defensive handling of null responses and missing data.</li>
 *   <li>Mapping of Feign client exceptions to domain-specific exceptions.</li>
 *   <li>Graceful handling of unexpected runtime errors.</li>
 * </ul>
 *
 * <p>
 * <b>Test characteristics:</b>
 * </p>
 * <ul>
 *   <li>Pure unit tests (no Spring context).</li>
 *   <li>Uses Mockito to simulate client behavior.</li>
 *   <li>Executes {@code @Async} methods synchronously (Spring not involved).</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class CustomerServiceImplTest {

    @Mock
    private ICustomerClient customerClient;

    private CustomerServiceImpl customerService;

    /**
     * Initializes the {@link CustomerServiceImpl} under test with a mocked
     * {@link ICustomerClient}.
     *
     * <p>
     * A new service instance is created before each test to ensure test
     * isolation and avoid shared state.
     * </p>
     */
    @BeforeEach
    void setUp() {
        customerService = new CustomerServiceImpl(customerClient);
    }

    /**
     * Verifies that a valid customer response is returned successfully when
     * the downstream customer service responds with valid data.
     */
    @Test
    void shouldReturnCustomerWhenResponseIsValid() throws Exception {
        // given
        final CustomerResponse customer
                = new CustomerResponse("123", "John", "Doe", "test_email@com");
        final ApiResponse<CustomerResponse> apiResponse =
                new ApiResponse<>(ApiResponse.Status.SUCCESS, customer, null);
        when(customerClient.findCustomerById("123"))
                .thenReturn(apiResponse);

        // when
        final CustomerResponse result =
                customerService.getCustomer("123").get();

        // then
        assertThat(result).isEqualTo(customer);
    }

    /**
     * Verifies that the service fails with {@link CustomerNotFoundExceptions}
     * when the downstream customer service returns a null response.
     *
     * <p>
     * This test ensures defensive handling of unexpected null responses.
     * </p>
     */
    @Test
    void shouldFailWhenResponseIsNull() {
        // given
        when(customerClient.findCustomerById("123"))
                .thenReturn(null);

        // when
        final CompletableFuture<CustomerResponse> future =
                customerService.getCustomer("123");

        // then
        assertThatThrownBy(future::join)
                .hasCauseInstanceOf(CustomerNotFoundExceptions.class)
                .hasMessageContaining("Customer data missing");
    }

    /**
     * Verifies that the service fails with {@link CustomerNotFoundExceptions}
     * when the response contains no customer data.
     *
     * <p>
     * This scenario represents a successful call with an invalid or incomplete
     * payload.
     * </p>
     */
    @Test
    void shouldFailWhenCustomerDataIsNull() {
        // given
        final ApiResponse<CustomerResponse> apiResponse =
                new ApiResponse<>(FAILED, null, null);
        when(customerClient.findCustomerById("123"))
                .thenReturn(apiResponse);

        // when
        final CompletableFuture<CustomerResponse> future =
                customerService.getCustomer("123");

        // then
        assertThatThrownBy(future::join)
                .hasCauseInstanceOf(CustomerNotFoundExceptions.class);
    }

    /**
     * Verifies that a Feign {@link feign.FeignException.NotFound} exception is
     * translated into a {@link CustomerNotFoundExceptions}.
     *
     * <p>
     * This scenario represents a valid request for a customer that does not
     * exist in the downstream service.
     * </p>
     */
    @Test
    void shouldFailWithCustomerNotFoundExceptionForFeign404() {
        // given
        final NotFound feign404 =
                mock(feign.FeignException.NotFound.class);
        when(feign404.status()).thenReturn(404);
        when(customerClient.findCustomerById("123"))
                .thenThrow(feign404);

        // when
        final CompletableFuture<CustomerResponse> future =
                customerService.getCustomer("123");

        // then
        assertThatThrownBy(future::join)
                .hasCauseInstanceOf(CustomerNotFoundExceptions.class)
                .hasMessageContaining("No customer exists");
    }

    /**
     * Verifies that non-404 {@link FeignException} instances are translated into
     * a {@link CustomerNotFoundExceptions} indicating a downstream service error.
     */
    @Test
    void shouldFailWithCustomerServiceErrorForFeignException() {
        // given
        final FeignException feignException =
                mock(feign.FeignException.class);
        when(feignException.status()).thenReturn(500);

        when(customerClient.findCustomerById("123"))
                .thenThrow(feignException);

        // when
        final CompletableFuture<CustomerResponse> future =
                customerService.getCustomer("123");

        // then
        assertThatThrownBy(future::join)
                .hasCauseInstanceOf(CustomerNotFoundExceptions.class)
                .hasMessageContaining("Customer service error");
    }

    /**
     * Verifies that unexpected runtime exceptions are handled gracefully and
     * translated into a {@link CustomerNotFoundExceptions}.
     *
     * <p>
     * This ensures that internal failures do not leak raw exceptions to callers.
     * </p>
     */
    @Test
    void shouldFailWithUnexpectedException() {
        // given
        when(customerClient.findCustomerById("123"))
                .thenThrow(new RuntimeException("boom"));

        // when
        final CompletableFuture<CustomerResponse> future =
                customerService.getCustomer("123");

        // then
        assertThatThrownBy(future::join)
                .hasCauseInstanceOf(CustomerNotFoundExceptions.class)
                .hasMessageContaining("Unexpected error");
    }
}
