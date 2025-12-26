package com.forsaken.ecommerce.order.orderline.service;

import com.forsaken.ecommerce.common.responses.PagedResponse;
import com.forsaken.ecommerce.order.orderline.dto.OrderLineResponse;
import com.forsaken.ecommerce.order.orderline.model.OrderLine;
import com.forsaken.ecommerce.order.orderline.repository.OrderLineRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrderLineServiceImpl implements IOrderLineService {

    private final OrderLineRepository orderLineRepository;

    @Override
    public PagedResponse<OrderLineResponse> findAllByOrderReference(
            final String orderReference,
            final int page,
            final int size
    ) {
        log.info("Find all Order Lines By Order Id: {}", orderReference);
        final int finalPage = Math.max(page - 1, 0);
        final int finalSize = Math.max(size, 1);
        final Pageable pageable = PageRequest.of(finalPage, finalSize);
        final Page<OrderLineResponse> orderLinePage =
                orderLineRepository
                        .findAllByOrder_Reference(orderReference, pageable)
                        .map(OrderLine::toOrderLineResponse);
        return PagedResponse.<OrderLineResponse>builder()
                .content(orderLinePage.getContent())
                .page(finalPage + 1)
                .size(finalSize)
                .totalElements(orderLinePage.getTotalElements())
                .totalPages(orderLinePage.getTotalPages())
                .build();
    }
}
