package com.forsaken.ecommerce.payment.controller;


import com.forsaken.ecommerce.avro.PaymentMethod;
import com.forsaken.ecommerce.common.responses.ApiResponse;
import com.forsaken.ecommerce.common.responses.PagedResponse;
import com.forsaken.ecommerce.payment.dto.Customer;
import com.forsaken.ecommerce.payment.dto.PaymentDto;
import com.forsaken.ecommerce.payment.dto.PaymentRequest;
import com.forsaken.ecommerce.payment.dto.PaymentSummaryDto;
import com.forsaken.ecommerce.payment.service.IPaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PaymentControllerImpl}, verifying controller-layer behavior
 * and ensuring correct interaction with the {@link IPaymentService}.
 *
 * <p>These tests mock the service layer to isolate controller logic and validate:
 * <ul>
 *     <li>Correct HTTP status mapping</li>
 *     <li>Proper construction of {@link ApiResponse} objects</li>
 *     <li>Correct delegation of calls to {@link IPaymentService}</li>
 *     <li>No unintended interactions with the service layer</li>
 * </ul>
 *
 * <p>The tests do not cover validation annotations or serialization concerns,
 * as those responsibilities belong to Spring MVC and separate integration tests.</p>
 */
class PaymentControllerImplTest {

    private IPaymentService paymentService;
    private PaymentControllerImpl controller;

    /**
     * Initializes mocks and the controller before each test.
     *
     * <p>Uses a mocked {@link IPaymentService} to ensure controller behavior
     * is tested in isolation.</p>
     */
    @BeforeEach
    void setUp() {
        paymentService = mock(IPaymentService.class);
        controller = new PaymentControllerImpl(paymentService);
    }

    /**
     * Verifies that {@link PaymentControllerImpl#createPayment(PaymentRequest)}
     * returns HTTP 201 (Created) and a successful {@link ApiResponse} when the
     * service successfully creates a payment.
     *
     * <p>This test ensures:</p>
     * <ul>
     *     <li>The controller passes the request to the service</li>
     *     <li>The returned payment ID is included in the response</li>
     *     <li>The correct success message is used</li>
     *     <li>No additional service calls are made</li>
     * </ul>
     */
    @Test
    void createPayment_ReturnsCreatedAndApiResponse() {
        // Given
        final PaymentRequest request = PaymentRequest.builder()
                .amount(new BigDecimal(100))
                .paymentMethod(PaymentMethod.BITCOIN)
                .orderId(123)
                .orderReference("test_order_123")
                .customer(
                        Customer.builder().build()
                )
                .build();
        final int expectedPaymentId = 123;
        when(paymentService.createPayment(request)).thenReturn(expectedPaymentId);

        // When
        final ResponseEntity<ApiResponse<Integer>> response = controller.createPayment(request);

        // Then
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        final ApiResponse<Integer> body = response.getBody();
        assertNotNull(body);
        assertEquals(ApiResponse.Status.SUCCESS, body.status());
        assertEquals(expectedPaymentId, body.data());
        assertEquals("Payment Initiated", body.message());
        verify(paymentService, times(1)).createPayment(request);
        verifyNoMoreInteractions(paymentService);
    }

    /**
     * Validates that {@link PaymentControllerImpl#getPaymentSummary(LocalDateTime, LocalDateTime, int, int)}
     * returns HTTP 201 (Created) and a correctly populated {@link ApiResponse} containing
     * the payment summary data returned by the service.
     *
     * <p>Specifically verifies:</p>
     * <ul>
     *     <li>Delegation to the service with correct parameters</li>
     *     <li>The summary object is passed back unchanged</li>
     *     <li>The response contains the expected success message</li>
     * </ul>
     */
    @Test
    void getPaymentSummary_ReturnsCreatedAndApiResponse() {
        // Given
        final LocalDateTime fromDate = LocalDateTime.of(2025, 1, 1, 0, 0);
        final LocalDateTime toDate = LocalDateTime.of(2025, 1, 31, 23, 59);
        final int page = 0;
        final int size = 10;
        @SuppressWarnings("unchecked")
        final PagedResponse<PaymentSummaryDto> pagedResponse = mock(PagedResponse.class);
        when(paymentService.getPaymentSummary(fromDate, toDate, page, size))
                .thenReturn(pagedResponse);

        // When
        final ResponseEntity<ApiResponse<PagedResponse<PaymentSummaryDto>>> response =
                controller.getPaymentSummary(fromDate, toDate, page, size);

        // Then
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        final ApiResponse<PagedResponse<PaymentSummaryDto>> body = response.getBody();
        assertNotNull(body);
        assertEquals(ApiResponse.Status.SUCCESS, body.status());
        assertSame(pagedResponse, body.data());
        assertEquals("Payment Summary", body.message());
        verify(paymentService, times(1))
                .getPaymentSummary(fromDate, toDate, page, size);
        verifyNoMoreInteractions(paymentService);
    }

    /**
     * Ensures that {@link PaymentControllerImpl#getAllPayments(LocalDateTime, LocalDateTime, int, int)}
     * returns HTTP 201 (Created) along with a successful {@link ApiResponse} containing
     * paginated payment results.
     *
     * <p>This test confirms:</p>
     * <ul>
     *     <li>Correct service invocation with all parameters</li>
     *     <li>The paginated payment result is returned intact</li>
     *     <li>The expected success message is included</li>
     *     <li>No additional service-layer interactions occur</li>
     * </ul>
     */
    @Test
    void getAllPayments_ReturnsCreatedAndApiResponse() {
        // Given
        final LocalDateTime fromDate = LocalDateTime.of(2025, 2, 1, 0, 0);
        final LocalDateTime toDate = LocalDateTime.of(2025, 2, 28, 23, 59);
        final int page = 1;
        final int size = 20;
        @SuppressWarnings("unchecked")
        final PagedResponse<PaymentDto> pagedPayments = mock(PagedResponse.class);
        when(paymentService.getAllPayments(fromDate, toDate, page, size))
                .thenReturn(pagedPayments);

        // When
        final ResponseEntity<ApiResponse<PagedResponse<PaymentDto>>> response =
                controller.getAllPayments(fromDate, toDate, page, size);

        // Then
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        final ApiResponse<PagedResponse<PaymentDto>> body = response.getBody();
        assertNotNull(body);
        assertEquals(ApiResponse.Status.SUCCESS, body.status());
        assertSame(pagedPayments, body.data());
        assertEquals("Fetched Payments", body.message());
        verify(paymentService, times(1))
                .getAllPayments(fromDate, toDate, page, size);
        verifyNoMoreInteractions(paymentService);
    }
}
