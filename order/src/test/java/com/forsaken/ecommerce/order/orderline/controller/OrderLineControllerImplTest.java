package com.forsaken.ecommerce.order.orderline.controller;

import com.forsaken.ecommerce.common.responses.ApiResponse;
import com.forsaken.ecommerce.common.responses.PagedResponse;
import com.forsaken.ecommerce.order.orderline.dto.OrderLineResponse;
import com.forsaken.ecommerce.order.orderline.service.IOrderLineService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OrderLineControllerImpl}.
 *
 * <p>
 * These tests validate the behavior of the Order Line REST controller in
 * isolation, ensuring that HTTP concerns such as status codes, response
 * envelopes, and delegation to the service layer are handled correctly.
 * </p>
 *
 * <p>
 * <b>Scope of testing:</b>
 * </p>
 * <ul>
 *     <li>The controller is tested without loading the Spring MVC context.</li>
 *     <li>{@link IOrderLineService} is mocked to isolate controller logic.</li>
 *     <li>HTTP response structure and metadata are verified explicitly.</li>
 * </ul>
 *
 * <p>
 * These tests ensure that the controller acts strictly as an orchestration
 * layer and does not contain business logic.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class OrderLineControllerImplTest {

    @Mock
    private IOrderLineService orderLineService;

    @InjectMocks
    private OrderLineControllerImpl controller;

    /**
     * Verifies that the controller returns an HTTP 200 (OK) response containing
     * a successful {@link ApiResponse} when valid request parameters are provided.
     *
     * <p>
     * This test validates the primary success path:
     * </p>
     * <ul>
     *     <li>Delegates the request to {@link IOrderLineService} with exact arguments.</li>
     *     <li>Wraps the service response in a standardized {@link ApiResponse} envelope.</li>
     *     <li>Sets the correct HTTP status code.</li>
     *     <li>Populates the response message with the order reference.</li>
     * </ul>
     *
     * <p>
     * The service layer is mocked, and no HTTP serialization or deserialization
     * is performed in this unit test.
     * </p>
     */
    @Test
    @DisplayName("Should return paged order lines for valid input")
    void shouldReturnPagedOrderLines_whenValidInput() {
        // given
        final String orderReference = "ORD-123";
        final int page = 1;
        final int size = 3;
        final OrderLineResponse line1 =
                new OrderLineResponse(1, 2);
        final OrderLineResponse line2 =
                new OrderLineResponse(2, 1);
        final PagedResponse<OrderLineResponse> pagedResponse =
                PagedResponse.<OrderLineResponse>builder()
                        .content(List.of(line1, line2))
                        .page(1)
                        .size(3)
                        .totalElements(2)
                        .totalPages(1)
                        .build();
        when(orderLineService.findAllByOrderReference(orderReference, page, size))
                .thenReturn(pagedResponse);

        // when
        final ResponseEntity<ApiResponse<PagedResponse<OrderLineResponse>>> response =
                controller.findByOrderReference(orderReference, page, size);

        // then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());

        final ApiResponse<PagedResponse<OrderLineResponse>> body = response.getBody();
        assertEquals(ApiResponse.Status.SUCCESS, body.status());
        assertEquals("Order Details Of Order Reference:: " + orderReference, body.message());

        final PagedResponse<OrderLineResponse> data = body.data();
        assertNotNull(data);
        assertEquals(2, data.content().size());
        assertEquals(1, data.page());
        assertEquals(3, data.size());
        assertEquals(2, data.totalElements());
        assertEquals(1, data.totalPages());
        verify(orderLineService)
                .findAllByOrderReference(orderReference, page, size);
        verifyNoMoreInteractions(orderLineService);
    }

    /**
     * Verifies that the controller propagates runtime exceptions thrown by
     * the service layer without suppressing or transforming them.
     *
     * <p>
     * This behavior is important when global exception handling (e.g.,
     * {@code @ControllerAdvice}) is responsible for translating exceptions
     * into HTTP error responses.
     * </p>
     *
     * <p>
     * The test ensures that:
     * </p>
     * <ul>
     *     <li>The controller does not catch or swallow service-layer exceptions.</li>
     *     <li>The original exception message is preserved.</li>
     *     <li>Delegation to the service occurs with the expected parameters.</li>
     * </ul>
     */
    @Test
    @DisplayName("Should propagate exception when service fails")
    void shouldThrowException_whenServiceThrows() {
        // given
        final String orderReference = "ORD-123";
        when(orderLineService.findAllByOrderReference(anyString(), anyInt(), anyInt()))
                .thenThrow(new RuntimeException("Service failure"));

        // when + then
        final RuntimeException ex = assertThrows(
                RuntimeException.class,
                () -> controller.findByOrderReference(orderReference, 1, 3)
        );
        assertEquals("Service failure", ex.getMessage());
        verify(orderLineService)
                .findAllByOrderReference(orderReference, 1, 3);
    }
}