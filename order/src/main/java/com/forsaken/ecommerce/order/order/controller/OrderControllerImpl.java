package com.forsaken.ecommerce.order.order.controller;

import com.forsaken.ecommerce.common.exceptions.BusinessException;
import com.forsaken.ecommerce.common.exceptions.CustomerNotFoundExceptions;
import com.forsaken.ecommerce.common.exceptions.PaymentFailedExceptions;
import com.forsaken.ecommerce.common.exceptions.ProductNotFoundExceptions;
import com.forsaken.ecommerce.common.responses.ApiResponse;
import com.forsaken.ecommerce.order.order.dto.OrderRequest;
import com.forsaken.ecommerce.order.order.dto.OrderResponse;
import com.forsaken.ecommerce.order.order.service.IOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutionException;

@RestController
@RequiredArgsConstructor
public class OrderControllerImpl implements IOrderController {

    private final IOrderService orderService;

    @Override
    public ResponseEntity<ApiResponse<Integer>> createOrder(final OrderRequest request)
            throws ExecutionException, InterruptedException, CustomerNotFoundExceptions,
            BusinessException, PaymentFailedExceptions, ProductNotFoundExceptions {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                        ApiResponse.<Integer>builder()
                                .status(ApiResponse.Status.SUCCESS)
                                .data(orderService.createOrder(request))
                                .message("Order Created Successfully.")
                                .build()
                );
    }

    @Override
    public ResponseEntity<ApiResponse<List<OrderResponse>>> findAll() {
        return ResponseEntity.status(HttpStatus.OK)
                .body(
                        ApiResponse.<List<OrderResponse>>builder()
                                .status(ApiResponse.Status.SUCCESS)
                                .data(orderService.findAllOrders())
                                .message("Find All Orders.")
                                .build()
                );
    }

    @Override
    public ResponseEntity<ApiResponse<List<OrderResponse>>> findAllOrdersByCustomerId(
            final String customerId,
            final LocalDateTime fromDate,
            final LocalDateTime toDate
    ) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(
                        ApiResponse.<List<OrderResponse>>builder()
                                .status(ApiResponse.Status.SUCCESS)
                                .data(orderService.findAllOrdersByCustomerId(customerId, fromDate, toDate))
                                .message("Find All Orders By Customer Id.")
                                .build()
                );
    }

    @Override
    public ResponseEntity<ApiResponse<OrderResponse>> findById(final Integer orderId) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(
                        ApiResponse.<OrderResponse>builder()
                                .status(ApiResponse.Status.SUCCESS)
                                .data(orderService.findById(orderId))
                                .message("Find By Order Id.")
                                .build()
                );
    }
}
