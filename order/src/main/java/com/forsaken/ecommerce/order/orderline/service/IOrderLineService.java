package com.forsaken.ecommerce.order.orderline.service;

import com.forsaken.ecommerce.common.responses.PagedResponse;
import com.forsaken.ecommerce.order.orderline.dto.OrderLineResponse;


/**
 * Service interface responsible for handling business operations
 * related to Order Line entities.
 *
 * <p>This service provides read-only access to Order Line data and
 * supports paginated retrieval of order line items associated with
 * a specific Order.</p>
 */
public interface IOrderLineService {

    /**
     * Retrieves paginated Order Line items for a given Order reference.
     *
     * <p>This method applies pagination at the persistence layer to ensure
     * efficient retrieval for orders containing a large number of line items.
     * Page indexing is expected to be <b>1-based</b> at the service boundary.</p>
     *
     * <p>If the specified order reference exists but contains no order lines,
     * an empty page is returned with valid pagination metadata.</p>
     *
     * <p><b>Error Handling:</b></p>
     * <ul>
     *     <li>Validation errors (e.g., invalid pagination parameters) should
     *         be handled at the service or controller layer.</li>
     *     <li>Business rules may optionally enforce order existence checks
     *         depending on application requirements.</li>
     * </ul>
     *
     * @param orderReference the unique reference of the Order whose line
     *                       items are to be retrieved; must not be blank
     * @param page the page number to retrieve (1-based index)
     * @param size the number of records per page
     *
     * @return a {@link PagedResponse} containing {@link OrderLineResponse}
     * objects and pagination metadata
     *
     * @throws IllegalArgumentException if pagination parameters are invalid
     */
    PagedResponse<OrderLineResponse> findAllByOrderReference(
            final String orderReference,
            final int page,
            final int size
    );
}
