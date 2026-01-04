package com.forsaken.ecommerce.payment.service;

import com.forsaken.ecommerce.common.responses.PagedResponse;
import com.forsaken.ecommerce.payment.dto.PaymentDto;
import com.forsaken.ecommerce.payment.dto.PaymentRequest;
import com.forsaken.ecommerce.payment.dto.PaymentSummaryDto;
import com.forsaken.ecommerce.payment.model.Payment;

import java.time.LocalDateTime;

/**
 * Service interface defining the core business operations for managing payments.
 *
 * <p>This interface abstracts payment-related operations such as payment creation,
 * generating aggregated summaries, and retrieving paginated payment records with
 * optional date filtering. Implementations are responsible for applying business
 * rules, validations, and orchestrating repository operations.</p>
 */
public interface IPaymentService {


    /**
     * Creates a new payment based on the provided request data.
     *
     * <p>This method validates and processes the incoming {@link PaymentRequest},
     * persists the payment information, and returns the generated payment ID.
     * The implementation is responsible for performing any required business
     * logic such as validation, fee calculation, or status initialization.</p>
     *
     * @param request the payment creation request containing all required payment fields;
     *                must not be null
     * @return the generated unique payment ID
     * @throws IllegalArgumentException if the request is null or contains invalid data
     */
    Integer createPayment(final PaymentRequest request);

    /**
     * Retrieves a paginated summary of payments grouped by payment method
     * for the given date range.
     *
     * @param fromDate optional lower bound (inclusive) of the payment creation date/time filter
     * @param toDate   optional upper bound (inclusive) of the payment creation date/time filter
     * @param page     zero-based page index
     * @param size     number of records per page
     * @return {@link PagedResponse} containing a page of {@link PaymentSummaryDto} items
     */
    PagedResponse<PaymentSummaryDto> getPaymentSummary(
            final LocalDateTime fromDate,
            final LocalDateTime toDate,
            final int page,
            final int size
    );

    /**
     * Retrieves a paginated list of payments filtered by an optional date-time range.
     *
     * <p>If {@code fromDate} and/or {@code toDate} are provided, only payments whose
     * {@code createdDate} falls within the specified interval are included. When both
     * parameters are {@code null}, the method may return all available payment records.</p>
     *
     * <p>Pagination is controlled using the {@code page} and {@code size} parameters.
     * The {@code page} value is expected to be 1-based (i.e., {@code page = 1} represents
     * the first page). Implementations may internally convert this to zero-based
     * indexing for repository operations.</p>
     *
     * @param fromDate optional lower bound (inclusive) of the payment creation timestamp;
     *                 may be {@code null} for no lower filter
     * @param toDate   optional upper bound (inclusive) of the payment creation timestamp;
     *                 may be {@code null} for no upper filter
     * @param page     the 1-based page number to retrieve
     * @param size     the number of records per page
     * @return a {@link PagedResponse} containing a page of {@link Payment} entities along
     *         with pagination metadata such as total elements and total pages
     */
    PagedResponse<PaymentDto> getAllPayments(
            final LocalDateTime fromDate,
            final LocalDateTime toDate,
            final int page,
            final int size
    );
}
