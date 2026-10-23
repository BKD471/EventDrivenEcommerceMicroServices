package com.forsaken.ecommerce.payment.controller;

import com.forsaken.ecommerce.common.responses.ApiResponse;
import com.forsaken.ecommerce.payment.dto.PaymentDailySummaryDto;
import com.forsaken.ecommerce.payment.service.PaymentReportService;
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
@RequestMapping("/api/v1/payments/reports")
@RequiredArgsConstructor
public class PaymentReportController {

    private final PaymentReportService reportService;

    /** Pre-aggregated COMPLETED payments per day and payment method, both dates inclusive. */
    @GetMapping("/daily")
    public ResponseEntity<ApiResponse<List<PaymentDailySummaryDto>>> getDailySummaries(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate to
    ) {
        return ResponseEntity.ok(ApiResponse.<List<PaymentDailySummaryDto>>builder()
                .status(ApiResponse.Status.SUCCESS)
                .data(reportService.findDailySummaries(from, to))
                .message("Daily payment summaries")
                .build());
    }
}
