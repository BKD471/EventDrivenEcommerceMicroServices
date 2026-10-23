package com.forsaken.ecommerce.order.order.controller;

import com.forsaken.ecommerce.common.responses.ApiResponse;
import com.forsaken.ecommerce.order.order.dto.OrderDailySalesDto;
import com.forsaken.ecommerce.order.order.service.OrderReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/orders/reports")
@RequiredArgsConstructor
public class OrderReportController {

    private final OrderReportService reportService;

    /** Pre-aggregated COMPLETED orders per day and payment method, both dates inclusive. */
    @GetMapping("/daily")
    public ResponseEntity<ApiResponse<List<OrderDailySalesDto>>> getDailySales(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate to
    ) {
        return ResponseEntity.ok(ApiResponse.<List<OrderDailySalesDto>>builder()
                .status(ApiResponse.Status.SUCCESS)
                .data(reportService.findDailySales(from, to))
                .message("Daily order sales")
                .build());
    }
}
