package com.forsaken.ecommerce.payment.service;


import com.forsaken.ecommerce.avro.PaymentConfirmation;
import com.forsaken.ecommerce.avro.PaymentMethod;
import com.forsaken.ecommerce.common.responses.PagedResponse;
import com.forsaken.ecommerce.payment.dto.Customer;
import com.forsaken.ecommerce.payment.dto.PaymentRequest;
import com.forsaken.ecommerce.payment.dto.PaymentSummaryDto;
import com.forsaken.ecommerce.payment.model.Payment;
import com.forsaken.ecommerce.payment.repository.IPaymentRepository;
import com.forsaken.ecommerce.payment.repository.PaymentSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PaymentServiceImpl}, validating core payment business logic
 * including payment creation, summary aggregation, and paginated retrieval.
 *
 * <p>This test suite isolates the service layer by mocking all dependencies:
 * <ul>
 *     <li>{@link IPaymentRepository} – to simulate persistence operations</li>
 *     <li>{@link INotificationProducerService} – to verify notification publishing</li>
 * </ul>
 *
 * <p>The tests ensure that:</p>
 * <ul>
 *     <li>Payment creation persists the correct entity</li>
 *     <li>Payment confirmation events are published to Kafka</li>
 *     <li>Summary queries return correctly transformed DTO results</li>
 *     <li>Pagination calculations and mappings behave as expected</li>
 * </ul>
 *
 * <p>No actual database or Kafka broker is used; Mockito enforces strict
 * interaction-based validation.</p>
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private IPaymentRepository repository;

    @Mock
    private INotificationProducerService notificationProducer;

    private PaymentServiceImpl service;

    /**
     * Initializes the service with mocked dependencies before each test execution.
     *
     * <p>This ensures predictable and isolated behavior, as all persistence
     * and messaging actions are replaced with Mockito stubs.</p>
     */
    @BeforeEach
    void setup() {
        service = new PaymentServiceImpl(repository, notificationProducer);
    }

    /**
     * Verifies that creating a payment:
     * <ul>
     *     <li>Converts the request to a {@link Payment} entity</li>
     *     <li>Persists the entity via the repository</li>
     *     <li>Returns the generated payment ID</li>
     *     <li>Emits a corresponding {@link PaymentConfirmation} notification</li>
     * </ul>
     *
     * <p>This is the most critical behavior of the payment module, ensuring both
     * persistence and event-driven integration workflows are triggered.</p>
     */
    @Test
    void testCreatePayment() {
        // Given
        final PaymentRequest paymentRequest = constructPaymentRequest(1);
        final Payment payment = paymentRequest.toPayment();
        final ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        doReturn(payment).when(repository).save(paymentCaptor.capture());

        // When
        final Integer result = service.createPayment(paymentRequest);

        // Then
        assertEquals(1, result);
        final Payment passedToSave = paymentCaptor.getValue();
        assertEquals(new BigDecimal("150.50"), passedToSave.getAmount());
        assertEquals(PaymentMethod.PAYPAL, passedToSave.getPaymentMethod());

        // Capture notification
        final ArgumentCaptor<PaymentConfirmation> notifCaptor =
                ArgumentCaptor.forClass(PaymentConfirmation.class);
        verify(notificationProducer).sendNotification(notifCaptor.capture());
        final PaymentConfirmation paymentConfirmation = notifCaptor.getValue();
        assertEquals("order-123", paymentConfirmation.getOrderReference());
        assertEquals("John", paymentConfirmation.getCustomerFirstname());
    }

    /**
     * Validates the payment summary aggregation logic.
     *
     * <p>This test ensures that the service:</p>
     * <ul>
     *     <li>Delegates to {@link IPaymentRepository#findPaymentSummaryBetween}</li>
     *     <li>Converts {@link PaymentSummary} projections into {@link PaymentSummaryDto}</li>
     *     <li>Wraps the results in a {@link PagedResponse} structure</li>
     * </ul>
     *
     * <p>The test uses a single mocked summary entry to verify DTO mapping.</p>
     */
    @Test
    void testGetPaymentSummary() {
        // Given
        final LocalDateTime fromDate = LocalDateTime.now().minusDays(5);
        final LocalDateTime toDate = LocalDateTime.now();
        final PageRequest pageable = PageRequest.of(0, 10);

        final PaymentSummary paypal = new PaymentSummary() {
            public PaymentMethod getPaymentMethod() {
                return PaymentMethod.PAYPAL;
            }

            public Long getCount() {
                return 2L;
            }

            public BigDecimal getTotalAmount() {
                return new BigDecimal("300");
            }
        };
        final Page<PaymentSummary> page = new PageImpl<>(List.of(paypal));
        when(repository.findPaymentSummaryBetween(eq(fromDate), eq(toDate), eq(pageable)))
                .thenReturn(page);

        // When
        final PagedResponse<PaymentSummaryDto> result =
                service.getPaymentSummary(fromDate, toDate, 1, 10);

        // Then
        assertEquals(1, result.content().size());
        assertEquals(PaymentMethod.PAYPAL, result.content().getFirst().paymentMethod());
        assertEquals(2L, result.content().getFirst().count());
        assertEquals(new BigDecimal("300"), result.content().getFirst().totalAmount());
    }

    /**
     * Tests paginated payment retrieval with date filtering.
     *
     * <p>This test verifies that the service:</p>
     * <ul>
     *     <li>Calls {@link IPaymentRepository#findAllByCreatedDateBetween}</li>
     *     <li>Returns a correctly populated {@link PagedResponse} including:</li>
     *     <ul>
     *         <li>content list</li>
     *         <li>page number adjustment (0 → 1)</li>
     *         <li>page size</li>
     *         <li>total elements</li>
     *     </ul>
     * </ul>
     *
     * <p>This ensures pagination semantics remain consistent with controller expectations.</p>
     */
    @Test
    void testGetAllPayments() {
        // Given
        final LocalDateTime fromDate = LocalDateTime.now().minusDays(5);
        final LocalDateTime toDate = LocalDateTime.now();
        final PageRequest pageable = PageRequest.of(0, 5);
        final Payment paymentOne = constructPaymentRequest(1).toPayment();
        final Payment paymentTwo = constructPaymentRequest(2).toPayment();
        final Page<Payment> page = new PageImpl<>(List.of(paymentOne, paymentTwo), pageable, 2);
        when(repository.findAllByCreatedDateBetween(eq(fromDate), eq(toDate), eq(pageable)))
                .thenReturn(page);

        // When
        PagedResponse<Payment> result =
                service.getAllPayments(fromDate, toDate, 1, 5);

        // Then
        assertEquals(2, result.content().size());
        assertEquals(1, result.page()); // +1 from Page index
        assertEquals(5, result.size());
        assertEquals(2, result.totalElements());
    }

    /**
     * Constructs a fully populated {@link PaymentRequest} object for convenient reuse
     * across test scenarios.
     *
     * @param paymentId the ID to assign to the request
     * @return a populated {@link PaymentRequest}
     */
    private PaymentRequest constructPaymentRequest(final int paymentId) {
        return PaymentRequest.builder()
                .id(paymentId)
                .amount(new BigDecimal("150.50"))
                .paymentMethod(PaymentMethod.PAYPAL)
                .orderId(123)
                .orderReference("order-123")
                .customer(constructCustomer())
                .build();
    }

    /**
     * Helper for building a mock {@link Customer} instance used in payment creation tests.
     *
     * @return a sample {@link Customer} object
     */
    private Customer constructCustomer() {
        return Customer.builder()
                .id("cust_123")
                .firstname("John")
                .lastname("Doe")
                .email("john@doe.com")
                .build();
    }
}
