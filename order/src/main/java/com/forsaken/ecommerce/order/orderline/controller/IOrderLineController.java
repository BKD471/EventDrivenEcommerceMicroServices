package com.forsaken.ecommerce.order.orderline.controller;

import com.forsaken.ecommerce.common.responses.ApiResponse;
import com.forsaken.ecommerce.common.responses.PagedResponse;
import com.forsaken.ecommerce.order.orderline.dto.OrderLineResponse;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * REST controller interface for managing Order Line resources.
 *
 * <p>This controller exposes read-only operations to retrieve order line
 * items associated with a specific Order. It is mapped under the base path
 * <b>/api/v1/order-lines</b>.</p>
 *
 * <p>All retrieval operations support pagination to ensure scalability
 * and prevent excessive data transfer for large orders.</p>
 */
@RequestMapping("/api/v1/order-lines")
@Validated
public interface IOrderLineController {

    /**
     * Retrieves paginated Order Line items for the given Order reference.
     *
     * <p>This endpoint returns a paginated list of {@link OrderLineResponse}
     * objects representing individual line items belonging to the specified
     * order reference.</p>
     *
     * <p>Pagination is applied at the database level to ensure efficient
     * retrieval. Page numbering is <b>1-based</b> for API consumers.</p>
     *
     * <p><b>Pagination Parameters:</b></p>
     * <ul>
     *     <li>{@code page} – Page number to retrieve (1-based, default = 1)</li>
     *     <li>{@code size} – Number of records per page (default = 3)</li>
     * </ul>
     *
     * <p>If the order exists but contains no line items, an empty page
     * is returned with valid pagination metadata.</p>
     *
     * @param orderReference the unique reference of the Order whose line
     *                       items should be fetched; must not be blank
     * @param page the page number to retrieve (1-based index)
     * @param size the number of records per page
     *
     * @return a {@link ResponseEntity} containing an {@link ApiResponse}
     * wrapping a {@link PagedResponse} of {@link OrderLineResponse}
     *
     * <p><b>Possible Responses:</b></p>
     * <ul>
     *     <li><b>200 OK</b> – Successfully retrieved paginated order line items.</li>
     *     <li><b>400 Bad Request</b> – Invalid pagination parameters or order reference.</li>
     *     <li><b>404 Not Found</b> – Order not found in the system.</li>
     *     <li><b>500 Internal Server Error</b> – Unexpected server failure.</li>
     * </ul>
     */
    @GetMapping("/order/{order-ref}")
    ResponseEntity<ApiResponse<PagedResponse<OrderLineResponse>>> findByOrderReference(
            @PathVariable("order-ref") @NotBlank final String orderReference,
            @RequestParam(name = "page", defaultValue = "1") final int page,
            @RequestParam(name = "size", defaultValue = "3") final int size
    );
}
