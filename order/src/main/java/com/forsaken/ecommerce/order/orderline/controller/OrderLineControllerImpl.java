package com.forsaken.ecommerce.order.orderline.controller;

import com.forsaken.ecommerce.common.responses.ApiResponse;
import com.forsaken.ecommerce.common.responses.PagedResponse;
import com.forsaken.ecommerce.order.orderline.dto.OrderLineResponse;
import com.forsaken.ecommerce.order.orderline.service.IOrderLineService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequiredArgsConstructor
public class OrderLineControllerImpl implements IOrderLineController {

    private final IOrderLineService orderLineService;

    @Override
    public ResponseEntity<ApiResponse<PagedResponse<OrderLineResponse>>> findByOrderReference(
            final String orderReference,
            final int page,
            final int size
    ) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(
                        ApiResponse.<PagedResponse<OrderLineResponse>>builder()
                                .status(ApiResponse.Status.SUCCESS)
                                .data(orderLineService.findAllByOrderReference(orderReference,page,size))
                                .message("Order Details Of Order Reference:: "+orderReference)
                                .build()
                );
    }
}
