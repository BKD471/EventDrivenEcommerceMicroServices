package com.forsaken.ecommerce.order.order.controller;

import com.forsaken.ecommerce.common.responses.ApiResponse;
import com.forsaken.ecommerce.common.responses.PagedResponse;
import com.forsaken.ecommerce.order.order.dto.OrderRequest;
import com.forsaken.ecommerce.order.order.dto.OrderResponse;
import com.forsaken.ecommerce.order.order.model.PaymentMethod;
import com.forsaken.ecommerce.order.order.service.IOrderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OrderControllerImpl}.
 *
 * <p>
 * These tests verify the controller's behavior in isolation by mocking
 * the {@link IOrderService} dependency and invoking controller methods
 * directly (without loading a Spring context).
 * </p>
 *
 * <p>
 * Scope of these tests:
 * </p>
 * <ul>
 *   <li>HTTP status codes returned by controller methods</li>
 *   <li>Correct wrapping of responses inside {@link ApiResponse}</li>
 *   <li>Proper delegation to {@link IOrderService}</li>
 * </ul>
 *
 * <p>
 * Out of scope:
 * </p>
 * <ul>
 *   <li>Request mapping and validation annotations</li>
 *   <li>Serialization / deserialization</li>
 *   <li>Exception handlers and filters</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class OrderControllerImplTest {

    @Mock
    private IOrderService orderService;

    @InjectMocks
    private OrderControllerImpl controller;

    /**
     * Verifies that an order is created successfully and a {@code 201 CREATED}
     * response is returned when the service layer completes without errors.
     */
    @Test
    void shouldCreateOrderSuccessfully() throws Exception {
        // given
        final OrderRequest request = constructOrderRequest(1, "Cust-123");
        final Integer orderId = 1001;
        when(orderService.createOrder(request)).thenReturn(orderId);

        // when
        final ResponseEntity<ApiResponse<Integer>> response =
                controller.createOrder(request);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status())
                .isEqualTo(ApiResponse.Status.SUCCESS);
        assertThat(response.getBody().data()).isEqualTo(orderId);
        assertThat(response.getBody().message())
                .isEqualTo("Order Created Successfully.");
        verify(orderService).createOrder(request);
    }

    /**
     * Verifies that a paginated list of all orders is returned successfully.
     */
    @Test
    void shouldReturnAllOrders() {
        // given
        final Integer page = 1;
        final Integer size = 10;
        final OrderResponse order1 = constructOrderRequest(1,"Cust-123").toOrder().fromOrder();
        final OrderResponse order2 = constructOrderRequest(2,"Cust-456").toOrder().fromOrder();
        final PagedResponse<OrderResponse> pagedResponse =
                new PagedResponse<>(
                        List.of(order1, order2),
                        page,
                        size,
                        2,
                        1,
                        true
                );
        when(orderService.findAllOrders(page, size))
                .thenReturn(pagedResponse);

        // when
        final ResponseEntity<ApiResponse<PagedResponse<OrderResponse>>> response =
                controller.findAll(page, size);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().content()).hasSize(2);
        assertThat(response.getBody().message())
                .isEqualTo("Find All Orders.");
        verify(orderService).findAllOrders(page, size);
    }

    /**
     * Verifies that orders for a specific customer are returned when
     * valid filter and pagination parameters are provided.
     */
    @Test
    void shouldReturnOrdersByCustomerId() {
        // given
        final String customerId = "CUST-1";
        final Integer page = 1;
        final Integer size = 5;
        final LocalDateTime fromDate = LocalDateTime.now().minusDays(10);
        final LocalDateTime toDate = LocalDateTime.now();
        final OrderResponse order = constructOrderRequest(1,"Cust-123").toOrder().fromOrder();
        final PagedResponse<OrderResponse> pagedResponse =
                new PagedResponse<>(
                        List.of(order),
                        page,
                        size,
                        1,
                        1,
                        true
                );
        when(orderService.findAllOrdersByCustomerId(
                customerId, fromDate, toDate, page, size
        )).thenReturn(pagedResponse);

        // when
        final ResponseEntity<ApiResponse<PagedResponse<OrderResponse>>> response =
                controller.findAllOrdersByCustomerId(
                        customerId, fromDate, toDate, page, size
                );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().content()).hasSize(1);
        assertThat(response.getBody().message())
                .isEqualTo("Find All Orders By Customer Id.");
        verify(orderService)
                .findAllOrdersByCustomerId(customerId, fromDate, toDate, page, size);
    }

    /**
     * Verifies that an order is returned successfully when queried by ID.
     */
    @Test
    void shouldReturnOrderById() {
        // given
        final Integer orderId = 10;
        final OrderResponse orderResponse = constructOrderRequest(1,"Cust-123").toOrder().fromOrder();;
        when(orderService.findById(orderId)).thenReturn(orderResponse);

        // when
        final ResponseEntity<ApiResponse<OrderResponse>> response =
                controller.findById(orderId);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data()).isEqualTo(orderResponse);
        assertThat(response.getBody().message())
                .isEqualTo("Find By Order Id.");
        verify(orderService).findById(orderId);
    }

    /**
     * Creates a minimal {@link OrderRequest} instance for testing purposes.
     *
     * @param id         order identifier
     * @param customerId customer identifier
     * @return a populated {@link OrderRequest}
     */
    private OrderRequest constructOrderRequest(
            final Integer id,
            final String customerId
    ) {
        return OrderRequest.builder()
                .id(id)
                .customerId(customerId)
                .paymentMethod(PaymentMethod.PAYPAL)
                .amount(BigDecimal.valueOf(500))
                .build();
    }
}