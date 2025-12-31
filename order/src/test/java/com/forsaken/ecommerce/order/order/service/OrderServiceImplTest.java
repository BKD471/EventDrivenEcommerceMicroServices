package com.forsaken.ecommerce.order.order.service;

import com.forsaken.ecommerce.common.exceptions.CustomerNotFoundExceptions;
import com.forsaken.ecommerce.common.responses.PagedResponse;
import com.forsaken.ecommerce.order.configs.general.OrderProperties;
import com.forsaken.ecommerce.order.customer.CustomerResponse;
import com.forsaken.ecommerce.order.customer.ICustomerService;
import com.forsaken.ecommerce.order.order.dto.OrderRequest;
import com.forsaken.ecommerce.order.order.dto.OrderResponse;
import com.forsaken.ecommerce.order.order.model.Order;
import com.forsaken.ecommerce.order.order.model.PaymentMethod;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;


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
    private Tracer tracer;
    @Mock
    private Tracer.SpanInScope spanInScope;
    @Mock
    private Span span;
    @Mock
    private TraceContext traceContext;

    @InjectMocks
    private OrderServiceImpl orderService;

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
        when(request.amount()).thenReturn(BigDecimal.TEN);
        when(request.paymentMethod()).thenReturn(
                PaymentMethod.CREDIT_CARD
        );
        when(request.reference()).thenReturn("ORD-1");
        when(request.toOrder()).thenReturn(order);
        when(customerService.getCustomer("C1"))
                .thenReturn(CompletableFuture.completedFuture(customer));
        when(productService.purchaseProducts(List.of()))
                .thenReturn(CompletableFuture.completedFuture(List.of(product)));
        when(orderRepository.save(order)).thenReturn(order);
        when(order.getId()).thenReturn(10);
        when(order.getReference()).thenReturn("ORD-1");
        when(tracer.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(traceContext);
        when(traceContext.traceId()).thenReturn("test-trace-id");

        // when
        final Integer result = orderService.createOrder(request);

        // then
        assertThat(result).isEqualTo(10);
        verify(customerService).getCustomer("C1");
        verify(productService).purchaseProducts(List.of());
        verify(orderRepository).save(order);
        verify(paymentService).pay(any(PaymentRequest.class));
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
        when(request.products()).thenReturn(List.of());
        when(customerService.getCustomer("C1"))
                .thenReturn(CompletableFuture.failedFuture(
                        new CustomerNotFoundExceptions("not found")));
        when(productService.purchaseProducts(List.of()))
                .thenReturn(CompletableFuture.completedFuture(List.of()));

        // when / then
        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(CustomerNotFoundExceptions.class);
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
