package com.forsaken.ecommerce.order.order.service;

import com.forsaken.ecommerce.common.exceptions.CustomerNotFoundExceptions;
import com.forsaken.ecommerce.common.exceptions.PaymentFailedExceptions;
import com.forsaken.ecommerce.common.responses.PagedResponse;
import com.forsaken.ecommerce.order.configs.general.OrderProperties;
import com.forsaken.ecommerce.order.customer.CustomerResponse;
import com.forsaken.ecommerce.order.customer.ICustomerService;
import com.forsaken.ecommerce.order.order.dto.OrderRequest;
import com.forsaken.ecommerce.order.order.dto.OrderResponse;
import com.forsaken.ecommerce.order.order.model.Order;
import com.forsaken.ecommerce.order.order.model.PaymentMethod;
import com.forsaken.ecommerce.order.order.model.OrderSagaStatus;
import com.forsaken.ecommerce.order.order.repository.IOrderRepository;
import com.forsaken.ecommerce.order.payment.IPaymentService;
import com.forsaken.ecommerce.order.payment.PaymentRequest;
import com.forsaken.ecommerce.order.product.IProductService;
import com.forsaken.ecommerce.order.product.PurchaseResponse;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import com.forsaken.ecommerce.common.exceptions.BusinessException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;


/**
 * Unit tests for {@link OrderServiceImpl}.
 *
 * <p>
 * These tests validate the business logic of the order service in isolation,
 * using Mockito to mock all external dependencies.
 * </p>
 *
 * <p>
 * Scope of these tests:
 * </p>
 * <ul>
 *   <li>Order creation workflow and orchestration</li>
 *   <li>Asynchronous dependency handling</li>
 *   <li>Pagination logic</li>
 *   <li>Customer-specific order retrieval</li>
 *   <li>Order lookup by ID</li>
 * </ul>
 *
 * <p>
 * Out of scope:
 * </p>
 * <ul>
 *   <li>Persistence behavior (covered by repository tests)</li>
 *   <li>HTTP request/response handling (covered by controller tests)</li>
 *   <li>Kafka, payment gateway, and external integrations</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private IOrderRepository orderRepository;
    @Mock
    private ICustomerService customerService;
    @Mock
    private IProductService productService;
    @Mock
    private IPaymentService paymentService;
    @Mock
    private IOrderProducer orderProducer;
    @Mock
    private OrderProperties orderProperties;
    @Mock
    private OrderCacheEvictor orderCacheEvictor;
    @Mock
    private Tracer tracer;
    @Mock
    private Tracer.SpanInScope spanInScope;
    @Mock
    private Span span;
    @Mock
    private TraceContext traceContext;

    @InjectMocks
    private OrderServiceImpl orderService;

    private void grantTransitions() {
        org.mockito.Mockito.lenient().when(orderRepository.transition(
                any(), any(), any(), any(), any())).thenReturn(1);
    }

    /**
     * Verifies that an order is created successfully when:
     * <ul>
     *   <li>customer lookup succeeds</li>
     *   <li>product purchase succeeds</li>
     *   <li>payment is triggered</li>
     *   <li>order confirmation is published</li>
     * </ul>
     */
    @Test
    void shouldCreateOrderSuccessfully() throws Exception {
        // given
        grantTransitions();
        final OrderRequest request = mock(OrderRequest.class);
        final Order order = mock(Order.class);
        final CustomerResponse customer = new CustomerResponse(
                "C1", "John", "Doe", "john@test.com"
        );
        final PurchaseResponse product = new PurchaseResponse(
                1, "P1", "Desc", BigDecimal.TEN, 2
        );
        when(request.customerId()).thenReturn("C1");
        when(request.products()).thenReturn(List.of());
        when(request.toOrder()).thenReturn(order);
        when(customerService.getCustomer("C1"))
                .thenReturn(CompletableFuture.completedFuture(customer));
        when(orderRepository.saveAndFlush(order)).thenReturn(order);
        when(order.getId()).thenReturn(10);
        when(order.getReference()).thenReturn("ORD-1");
        when(order.getTotalAmount()).thenReturn(BigDecimal.TEN);
        when(order.getPaymentMethod()).thenReturn(PaymentMethod.CREDIT_CARD);
        when(productService.reserveProducts("ORD-1", List.of()))
                .thenReturn(List.of(product));
        when(tracer.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(traceContext);
        when(traceContext.traceId()).thenReturn("test-trace-id");

        // when
        final Integer result = orderService.createOrder(request);

        // then
        assertThat(result).isEqualTo(10);
        verify(customerService).getCustomer("C1");
        verify(productService).reserveProducts("ORD-1", List.of());
        verify(orderRepository).transition(
                eq(10), any(), any(), eq(OrderSagaStatus.PENDING), eq(OrderSagaStatus.CONFIRMING));
        verify(orderRepository).transition(
                eq(10), any(), any(), eq(OrderSagaStatus.CONFIRMING), eq(OrderSagaStatus.COMPLETED));
        verify(orderCacheEvictor).evictAll();
        verify(paymentService).pay(any(PaymentRequest.class));
        verify(paymentService).confirm(10);
        verify(orderProducer).sendOrderConfirmation(any());
    }

    /**
     * Verifies that a {@link CustomerNotFoundExceptions} is propagated
     * when customer lookup fails during order creation.
     */
    @Test
    void shouldThrowCustomerNotFoundException() {
        // given
        final OrderRequest request = mock(OrderRequest.class);
        when(request.customerId()).thenReturn("C1");
        when(customerService.getCustomer("C1"))
                .thenReturn(CompletableFuture.failedFuture(
                        new CustomerNotFoundExceptions("not found")));

        // when / then
        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(CustomerNotFoundExceptions.class);
    }

    @Test
    void shouldCompensateInventoryAndPaymentWhenPaymentFails() throws Exception {
        grantTransitions();
        final OrderRequest request = mock(OrderRequest.class);
        final Order order = Order.builder()
                .id(11)
                .reference("ORD-FAIL")
                .build();
        final CustomerResponse customer = new CustomerResponse(
                "C1", "John", "Doe", "john@test.com"
        );
        when(request.customerId()).thenReturn("C1");
        when(request.products()).thenReturn(List.of());
        when(request.toOrder()).thenReturn(order);
        when(customerService.getCustomer("C1"))
                .thenReturn(CompletableFuture.completedFuture(customer));
        when(orderRepository.saveAndFlush(order)).thenReturn(order);
        when(productService.reserveProducts("ORD-FAIL", List.of())).thenReturn(List.of());
        doThrow(new PaymentFailedExceptions("payment declined"))
                .when(paymentService).pay(any(PaymentRequest.class));

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(PaymentFailedExceptions.class);

        verify(paymentService).cancel(11);
        verify(productService).releaseReservation("ORD-FAIL");
        assertThat(order.getSagaStatus()).isEqualTo(OrderSagaStatus.FAILED);
    }

    @Test
    void shouldNotCompensateWhenOwnershipIsLost() throws Exception {
        final OrderRequest request = mock(OrderRequest.class);
        final Order order = Order.builder().id(12).reference("ORD-LOST").build();
        when(request.customerId()).thenReturn("C1");
        when(request.products()).thenReturn(List.of());
        when(request.toOrder()).thenReturn(order);
        when(customerService.getCustomer("C1")).thenReturn(CompletableFuture.completedFuture(
                new CustomerResponse("C1", "John", "Doe", "john@test.com")));
        when(orderRepository.saveAndFlush(order)).thenReturn(order);
        when(orderRepository.transition(any(), any(), any(), any(), any())).thenReturn(0);
        when(productService.reserveProducts("ORD-LOST", List.of())).thenReturn(List.of());

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(BusinessException.class);

        verify(paymentService, never()).cancel(any());
        verify(productService, never()).releaseReservation(any());
    }

    @Test
    void shouldNotCompensateWhenPaymentConfirmationOutcomeIsUncertain() throws Exception {
        grantTransitions();
        final OrderRequest request = mock(OrderRequest.class);
        final Order order = Order.builder().id(14).reference("ORD-CONF").build();
        when(request.customerId()).thenReturn("C1");
        when(request.products()).thenReturn(List.of());
        when(request.toOrder()).thenReturn(order);
        when(customerService.getCustomer("C1")).thenReturn(CompletableFuture.completedFuture(
                new CustomerResponse("C1", "John", "Doe", "john@test.com")));
        when(orderRepository.saveAndFlush(order)).thenReturn(order);
        when(productService.reserveProducts("ORD-CONF", List.of())).thenReturn(List.of());
        doThrow(new IllegalStateException("timeout")).when(paymentService).confirm(14);

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(BusinessException.class);

        verify(paymentService, never()).cancel(any());
        verify(productService, never()).releaseReservation(any());
        verify(orderRepository).transition(
                eq(14), any(), any(), eq(OrderSagaStatus.PENDING), eq(OrderSagaStatus.CONFIRMING));
        verify(orderRepository).releaseClaim(eq(14), any());
        assertThat(order.getCustomerEmail()).isEqualTo("john@test.com");
    }

    @Test
    void shouldNotCompensateWhenConfirmationPublicationFails() throws Exception {
        grantTransitions();
        final OrderRequest request = mock(OrderRequest.class);
        final Order order = Order.builder()
                .id(13)
                .reference("ORD-PUB")
                .totalAmount(BigDecimal.TEN)
                .paymentMethod(com.forsaken.ecommerce.order.order.model.PaymentMethod.CREDIT_CARD)
                .build();
        when(request.customerId()).thenReturn("C1");
        when(request.products()).thenReturn(List.of());
        when(request.toOrder()).thenReturn(order);
        when(customerService.getCustomer("C1")).thenReturn(CompletableFuture.completedFuture(
                new CustomerResponse("C1", "John", "Doe", "john@test.com")));
        when(orderRepository.saveAndFlush(order)).thenReturn(order);
        when(productService.reserveProducts("ORD-PUB", List.of())).thenReturn(List.of());
        doThrow(new IllegalStateException("kafka down")).when(orderProducer).sendOrderConfirmation(any());

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(BusinessException.class);

        verify(paymentService, never()).cancel(any());
        verify(productService, never()).releaseReservation(any());
        verify(orderRepository, never()).transition(
                any(), any(), any(), eq(OrderSagaStatus.CONFIRMING), eq(OrderSagaStatus.COMPLETED));
        verify(orderRepository).releaseClaim(eq(13), any());
    }

    @Test
    void shouldReportNotClaimedWhenAnotherWorkerHoldsTheOrder() {
        final Order order = Order.builder().id(20).reference("ORD-CLAIMED").build();
        when(orderRepository.findById(20)).thenReturn(java.util.Optional.of(order));
        when(orderRepository.claim(eq(20), any(), any(), any(), any())).thenReturn(0);

        assertThat(orderService.recover(20)).isEqualTo(SagaRecoveryOutcome.NOT_CLAIMED);
        assertThat(orderService.retryCompensation(20)).isEqualTo(SagaRecoveryOutcome.NOT_CLAIMED);

        verify(orderRepository, never()).findDetailedById(any());
    }

    @Test
    void shouldReportNotClaimedWhenOrderIsGone() {
        when(orderRepository.findById(21)).thenReturn(java.util.Optional.empty());

        assertThat(orderService.recover(21)).isEqualTo(SagaRecoveryOutcome.NOT_CLAIMED);
    }

    @Test
    void shouldCompleteCompensationRetryAndMarkOrderFailed() {
        grantTransitions();
        final Order order = Order.builder().id(22).reference("ORD-COMP").build();
        when(orderRepository.findById(22)).thenReturn(java.util.Optional.of(order));
        when(orderRepository.claim(eq(22), any(), any(), any(), any())).thenReturn(1);

        assertThat(orderService.retryCompensation(22)).isEqualTo(SagaRecoveryOutcome.RECOVERED);

        verify(orderRepository).transition(
                eq(22), any(), any(), eq(OrderSagaStatus.COMPENSATION_REQUIRED), eq(OrderSagaStatus.FAILED));
    }

    @Test
    void shouldFailRecoveryAndReleaseClaimWhenCompensationStillFails() throws Exception {
        final Order order = Order.builder().id(23).reference("ORD-COMP-FAIL").build();
        when(orderRepository.findById(23)).thenReturn(java.util.Optional.of(order));
        when(orderRepository.claim(eq(23), any(), any(), any(), any())).thenReturn(1);
        doThrow(new IllegalStateException("payment down")).when(paymentService).cancel(23);

        assertThatThrownBy(() -> orderService.retryCompensation(23))
                .isInstanceOf(SagaRecoveryException.class);

        verify(orderRepository).releaseClaim(eq(23), any());
        verify(orderRepository, never()).transition(
                any(), any(), any(), eq(OrderSagaStatus.COMPENSATION_REQUIRED), eq(OrderSagaStatus.FAILED));
    }

    /**
     * Verifies that all orders are returned in a paginated response
     * when no page or size parameters are provided.
     */
    @Test
    void shouldReturnPagedOrders() {
        // given
        when(orderProperties.defaultPageSize()).thenReturn(10);
        final Order order = mock(Order.class);
        final Page<Order> page = new PageImpl<>(List.of(order));
        final Pageable pageable = PageRequest.of(
                0, 10, Sort.by("createdDate").descending()
        );
        when(orderRepository.findAll(pageable)).thenReturn(page);

        // when
        final PagedResponse<OrderResponse> result =
                orderService.findAllOrders(null, null);

        // then
        assertThat(result.totalElements()).isEqualTo(1);
        verify(orderRepository).findAll(pageable);
    }

    /**
     * Verifies that orders are returned correctly when filtering
     * by customer ID and creation date range.
     */
    @Test
    void shouldReturnOrdersByCustomerId() {
        // given
        when(orderProperties.defaultPageSize()).thenReturn(10);
        final LocalDateTime from = LocalDateTime.now().minusDays(10);
        final LocalDateTime to = LocalDateTime.now();
        final Order order = mock(Order.class);
        final Page<Order> page = new PageImpl<>(List.of(order));

        final Pageable pageable = PageRequest.of(
                0, 10, Sort.by("createdDate").descending()
        );

        when(orderRepository.findAllByCustomerIdAndCreatedDateBetween(
                "C1", from, to, pageable
        )).thenReturn(page);

        // when
        final PagedResponse<OrderResponse> result =
                orderService.findAllOrdersByCustomerId(
                        "C1", from, to, null, null
                );

        // then
        assertThat(result.totalElements()).isEqualTo(1);
        verify(orderRepository)
                .findAllByCustomerIdAndCreatedDateBetween(
                        "C1", from, to, pageable
                );
    }

    /**
     * Verifies that an order is returned successfully when queried by ID.
     */
    @Test
    void shouldReturnOrderById() {
        // given
        final Integer orderId = 1;
        final Order realOrder = Order.builder()
                .id(orderId)
                .customerId("CUST-1")
                .reference("ORD-1")
                .totalAmount(BigDecimal.TEN)
                .build();
        when(orderRepository.findById(orderId))
                .thenReturn(Optional.of(realOrder));

        // when
        final OrderResponse response = orderService.findById(orderId);

        // then
        assertThat(response).isNotNull();
        assertThat(response.reference()).isEqualTo("ORD-1");
        verify(orderRepository).findById(orderId);
    }

    /**
     * Verifies that an {@link jakarta.persistence.EntityNotFoundException}
     * is thrown when an order with the given ID does not exist.
     */
    @Test
    void shouldThrowWhenOrderNotFound() {
        when(orderRepository.findById(any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.findById(99))
                .isInstanceOf(jakarta.persistence.EntityNotFoundException.class);
    }
}
