package com.forsaken.ecommerce.order.orderline.service;

import com.forsaken.ecommerce.common.responses.PagedResponse;
import com.forsaken.ecommerce.order.orderline.dto.OrderLineResponse;
import com.forsaken.ecommerce.order.orderline.model.OrderLine;
import com.forsaken.ecommerce.order.orderline.repository.OrderLineRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OrderLineServiceImpl}.
 *
 * <p>
 * This test suite validates the business behavior of the Order Line service
 * layer in isolation, without loading the Spring context or interacting with
 * a real database.
 * </p>
 *
 * <p>
 * <b>Testing strategy:</b>
 * </p>
 * <ul>
 *     <li>The {@link OrderLineRepository} is mocked using Mockito.</li>
 *     <li>Pagination normalization logic (1-based → 0-based) is verified.</li>
 *     <li>Entity-to-DTO mapping via {@link OrderLine#toOrderLineResponse()}
 *     is explicitly validated.</li>
 *     <li>Exact argument matching is enforced for positive paths.</li>
 *     <li>{@code any()} matchers are used only for defensive and edge-case scenarios.</li>
 * </ul>
 *
 * <p>
 * These tests ensure that the service layer correctly orchestrates pagination,
 * delegates data access to the repository, and returns a properly populated
 * {@link PagedResponse}.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class OrderLineServiceImplTest {

    @Mock
    private OrderLineRepository orderLineRepository;

    @InjectMocks
    private OrderLineServiceImpl orderLineService;

    /**
     * Verifies that the service returns a correctly populated {@link PagedResponse}
     * when valid pagination parameters and a valid order reference are provided.
     *
     * <p>
     * This test covers the primary success path:
     * </p>
     * <ul>
     *     <li>Converts the 1-based page index from the API boundary to a 0-based
     *     index for persistence access.</li>
     *     <li>Delegates the query to the repository with an exact {@link Pageable}.</li>
     *     <li>Maps {@link OrderLine} entities to {@link OrderLineResponse} DTOs.</li>
     *     <li>Preserves pagination metadata in the returned response.</li>
     * </ul>
     *
     * <p>
     * No argument matchers ({@code any()}) are used in this test to ensure strict
     * verification of repository interaction.
     * </p>
     */
    @Test
    void shouldReturnPagedOrderLinesForValidInput() {
        // given
        final String orderReference = "ORD-123";
        final int page = 1;
        final int size = 10;
        final Pageable expectedPageable = PageRequest.of(0, 10);
        final OrderLine orderLineOne = mock(OrderLine.class);
        final OrderLine orderLineTwo = mock(OrderLine.class);
        final OrderLineResponse orderLineResponseOne = mock(OrderLineResponse.class);
        final OrderLineResponse orderLineResponseTwo = mock(OrderLineResponse.class);
        when(orderLineOne.toOrderLineResponse()).thenReturn(orderLineResponseOne);
        when(orderLineTwo.toOrderLineResponse()).thenReturn(orderLineResponseTwo);
        final Page<OrderLine> orderLinePage =
                new PageImpl<>(List.of(orderLineOne, orderLineTwo), expectedPageable, 2);
        when(orderLineRepository.findAllByOrder_Reference(orderReference, expectedPageable))
                .thenReturn(orderLinePage);

        // when
        final PagedResponse<OrderLineResponse> result =
                orderLineService.findAllByOrderReference(orderReference, page, size);

        // then
        assertThat(result).isNotNull();
        assertThat(result.content()).containsExactly(orderLineResponseOne, orderLineResponseTwo);
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.size()).isEqualTo(10);
        assertThat(result.totalElements()).isEqualTo(2);
        assertThat(result.totalPages()).isEqualTo(1);
        verify(orderLineRepository, times(1))
                .findAllByOrder_Reference(orderReference, expectedPageable);
        verifyNoMoreInteractions(orderLineRepository);
    }

    /**
     * Verifies that the service defaults the page number to {@code 1}
     * when an invalid page index ({@code 0}) is provided.
     *
     * <p>
     * This test ensures defensive handling of pagination inputs at the service
     * boundary, preventing negative or invalid page indexes from propagating
     * to the persistence layer.
     * </p>
     */
    @Test
    void shouldDefaultPageToOneWhenPageIsZero() {
        // given
        final Pageable expectedPageable = PageRequest.of(0, 5);
        when(orderLineRepository.findAllByOrder_Reference(any(), any()))
                .thenReturn(Page.empty(expectedPageable));

        // when
        final PagedResponse<OrderLineResponse> result =
                orderLineService.findAllByOrderReference("ORD-123", 0, 5);

        // then
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.content()).isEmpty();
        verify(orderLineRepository)
                .findAllByOrder_Reference(eq("ORD-123"), eq(expectedPageable));
    }

    /**
     * Verifies that the service defaults the page size to {@code 1}
     * when an invalid size value ({@code 0}) is provided.
     *
     * <p>
     * This prevents illegal {@link Pageable} configurations and ensures
     * a minimum page size is always enforced.
     * </p>
     */
    @Test
    void shouldDefaultSizeToOneWhenSizeIsZero() {
        // given
        final Pageable expectedPageable = PageRequest.of(0, 1);
        when(orderLineRepository.findAllByOrder_Reference(any(), any()))
                .thenReturn(Page.empty(expectedPageable));

        // when
        final PagedResponse<OrderLineResponse> result =
                orderLineService.findAllByOrderReference("ORD-123", 1, 0);

        // then
        assertThat(result.size()).isEqualTo(1);
        verify(orderLineRepository)
                .findAllByOrder_Reference(eq("ORD-123"), eq(expectedPageable));
    }

    /**
     * Verifies that the service returns an empty {@link PagedResponse}
     * when the specified order reference exists but has no associated
     * order line records.
     *
     * <p>
     * The response should:
     * </p>
     * <ul>
     *     <li>Contain an empty content list</li>
     *     <li>Report zero total elements</li>
     *     <li>Report zero total pages</li>
     * </ul>
     *
     * <p>
     * This behavior is important for client-side pagination handling
     * and UI consistency.
     * </p>
     */
    @Test
    void shouldReturnEmptyPageWhenNoOrderLinesExist() {
        // given
        final Pageable pageable = PageRequest.of(0, 10);
        when(orderLineRepository.findAllByOrder_Reference(any(), any()))
                .thenReturn(Page.empty(pageable));

        // when
        final PagedResponse<OrderLineResponse> result =
                orderLineService.findAllByOrderReference("ORD-999", 1, 10);

        // then
        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isZero();
        assertThat(result.totalPages()).isZero();
    }
}
