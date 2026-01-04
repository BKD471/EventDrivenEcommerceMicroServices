package com.forsaken.ecommerce.payment.controller;


import com.forsaken.ecommerce.common.responses.ApiResponse;
import com.forsaken.ecommerce.common.responses.PagedResponse;
import com.forsaken.ecommerce.payment.dto.PaymentDto;
import com.forsaken.ecommerce.payment.dto.PaymentRequest;
import com.forsaken.ecommerce.payment.dto.PaymentSummaryDto;
import com.forsaken.ecommerce.payment.model.Payment;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;

/**
 * REST controller contract for managing payment operations.
 *
 * <p>This interface defines endpoints for creating payments, retrieving
 * payment summaries grouped by payment method, and fetching paginated
 * payment records with optional date-range filters.</p>
 *
 * <p>All responses are wrapped in a standardized {@link ApiResponse}
 * structure to ensure consistency across the API.</p>
 */
@RequestMapping("/api/v1/payments")
public interface IPaymentController {

    /**
     * Creates a new payment record.
     *
     * <p>This endpoint accepts a {@link PaymentRequest} object containing all required
     * payment details. The request body is validated, and upon successful creation,
     * the API returns the generated payment ID.</p>
     *
     * @param request the payment creation request containing all necessary fields;
     *                must be valid and non-null
     * @return a ResponseEntity containing an ApiResponse wrapping the generated
     * payment ID (Integer)
     * @throws MethodArgumentNotValidException if the request body fails validation
     */
    @PostMapping
    ResponseEntity<ApiResponse<Integer>> createPayment(
            @RequestBody @Valid final PaymentRequest request
    );

    /**
     * Retrieves a paginated summary of payments grouped by payment method for the specified date range.
     *
     * <p>This endpoint aggregates payment records within the optional {@code fromDate} and {@code toDate}
     * filters, groups them by payment method, and returns a paginated list of summaries. If no date values
     * are provided, the implementation may return summaries for the full dataset.</p>
     *
     * <p>Pagination is controlled through the {@code page} and {@code size} parameters. The page index is
     * 1-based, meaning {@code page = 1} refers to the first page of results.</p>
     *
     * @param fromDate optional start of the date-time range filter (ISO-8601 format);
     *                 if omitted, no lower bound is applied
     * @param toDate   optional end of the date-time range filter (ISO-8601 format);
     *                 if omitted, no upper bound is applied
     * @param page     the page number to retrieve (1-based); defaults to 1
     * @param size     the number of records per page; defaults to 3
     * @return {@link ApiResponse} wrapping a {@link PagedResponse} containing
     *         {@link PaymentSummaryDto} items and pagination metadata
     */
    @GetMapping("/summary")
    ResponseEntity<ApiResponse<PagedResponse<PaymentSummaryDto>>> getPaymentSummary(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) final LocalDateTime fromDate,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) final LocalDateTime toDate,

            @RequestParam(name = "page", defaultValue = "1") final int page,
            @RequestParam(name = "size", defaultValue = "3") final int size
    );

    /**
     * Retrieves a paginated list of payments filtered by an optional date-time range.
     *
     * <p>If {@code fromDate} and/or {@code toDate} are provided, only payments whose
     * {@code createdDate} falls within the specified interval are included. If both
     * parameters are omitted, all available payments may be returned.</p>
     *
     * <p>Pagination is controlled via the {@code page} and {@code size} parameters.
     * The page index is 1-based, meaning {@code page = 1} corresponds to the first page
     * of results.</p>
     *
     * @param fromDate optional start of the date-time filter (ISO-8601 format);
     *                 if null, no lower bound is applied
     * @param toDate   optional end of the date-time filter (ISO-8601 format);
     *                 if null, no upper bound is applied
     * @param page     1-based page number to retrieve; defaults to 1
     * @param size     number of records per page; defaults to 3
     * @return {@link ApiResponse} wrapping a {@link PagedResponse} containing
     *         {@link Payment} entities and pagination metadata
     */
    @GetMapping("/allPayments")
    ResponseEntity<ApiResponse<PagedResponse<PaymentDto>>> getAllPayments(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) final LocalDateTime fromDate,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) final LocalDateTime toDate,

            @RequestParam(name = "page", defaultValue = "1") final int page,
            @RequestParam(name = "size", defaultValue = "3") final int size
    );
}
