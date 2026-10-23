package com.forsaken.ecommerce.payment.controller;

import com.forsaken.ecommerce.common.responses.ApiResponse;
import com.forsaken.ecommerce.common.responses.PagedResponse;
import com.forsaken.ecommerce.payment.dto.PaymentDto;
import com.forsaken.ecommerce.payment.dto.PaymentReconciliationDto;
import com.forsaken.ecommerce.payment.dto.PaymentRequest;
import com.forsaken.ecommerce.payment.model.PaymentStatus;
import com.forsaken.ecommerce.payment.dto.PaymentSummaryDto;
import com.forsaken.ecommerce.payment.service.IPaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class PaymentControllerImpl implements IPaymentController {

    private final IPaymentService paymentService;

    @Override
    public ResponseEntity<ApiResponse<Integer>> createPayment(
            final PaymentRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                        ApiResponse.<Integer>builder()
                                .status(ApiResponse.Status.SUCCESS)
                                .data(paymentService.createPayment(request))
                                .message("Payment Initiated")
                                .build()
                );
    }

    @Override
    public ResponseEntity<ApiResponse<Void>> confirmPayment(final Integer orderId) {
        paymentService.confirmPayment(orderId);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .status(ApiResponse.Status.SUCCESS)
                .message("Payment confirmed.")
                .build());
    }

    @Override
    public ResponseEntity<ApiResponse<Void>> cancelPayment(final Integer orderId) {
        paymentService.cancelPayment(orderId);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .status(ApiResponse.Status.SUCCESS)
                .message("Payment cancelled.")
                .build());
    }

    @Override
    public ResponseEntity<ApiResponse<PagedResponse<PaymentSummaryDto>>> getPaymentSummary(
            final LocalDateTime fromDate,
            final LocalDateTime toDate,
            final int page,
            final int size
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                        ApiResponse.<PagedResponse<PaymentSummaryDto>>builder()
                                .status(ApiResponse.Status.SUCCESS)
                                .data(paymentService.getPaymentSummary(fromDate, toDate, page, size))
                                .message("Payment Summary")
                                .build()
                );
    }

    @Override
    public ResponseEntity<ApiResponse<PagedResponse<PaymentDto>>> getAllPayments(
            final LocalDateTime fromDate,
            final LocalDateTime toDate,
            final int page,
            final int size
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                        ApiResponse.<PagedResponse<PaymentDto>>builder()
                                .status(ApiResponse.Status.SUCCESS)
                                .data(paymentService.getAllPayments(fromDate, toDate, page, size))
                                .message("Fetched Payments")
                                .build()
                );
    }

    @Override
    public ResponseEntity<ApiResponse<PaymentStatus>> getPaymentStatus(final Integer orderId) {
        return ResponseEntity.ok(ApiResponse.<PaymentStatus>builder()
                .status(ApiResponse.Status.SUCCESS)
                .data(paymentService.findStatusByOrderId(orderId))
                .message("Payment status")
                .build());
    }

    @Override
    public ResponseEntity<ApiResponse<List<PaymentReconciliationDto>>> getPaymentsForReconciliation(
            final PaymentStatus status,
            final LocalDateTime olderThan,
            final Integer afterId,
            final int size
    ) {
        return ResponseEntity.ok(ApiResponse.<List<PaymentReconciliationDto>>builder()
                .status(ApiResponse.Status.SUCCESS)
                .data(paymentService.findForReconciliation(status, olderThan, afterId, size))
                .message("Payments for reconciliation")
                .build());
    }
}
