package com.forsaken.ecommerce.product.service;

import com.forsaken.ecommerce.common.exceptions.ProductNotFoundExceptions;
import com.forsaken.ecommerce.common.responses.PagedResponse;
import com.forsaken.ecommerce.product.dto.ProductPurchaseRequest;
import com.forsaken.ecommerce.product.dto.ProductPurchaseResponse;
import com.forsaken.ecommerce.product.dto.ProductRequest;
import com.forsaken.ecommerce.product.dto.ProductResponse;
import com.forsaken.ecommerce.product.exceptions.CategoryNotFoundExceptions;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static com.forsaken.ecommerce.product.dto.ProductRequest.Direction;

/**
 * Service interface defining business operations for managing products, including
 * creation, lookup, filtering, purchasing, and pagination support.
 *
 * <p>All methods return domain-specific response DTOs rather than entities,
 * ensuring clean separation between persistence and business layers.
 */
public interface IProductService {

    /**
     * Creates a new product in the system.
     *
     * <p>This method performs validation, applies required business rules, and
     * persists the product to the database. On success, the generated product ID
     * is returned.
     *
     * @param request the product creation request; must not be {@code null}
     * @return the generated product identifier
     */
    Integer createProduct(final ProductRequest request);


    /**
     * Retrieves a paginated list of products.
     *
     * <p>Optionally includes signed URLs for product images when {@code signedUrls}
     * is set to {@code true}.
     *
     * @param signedUrls whether to include signed URLs for product images
     * @param page       the page index, starting from 1
     * @param size       the number of items per page
     * @return a {@link PagedResponse} containing a list of {@link ProductResponse} objects
     */
    PagedResponse<ProductResponse> getAllProducts(
            final Boolean signedUrls,
            final Integer page,
            final Integer size
    );


    /**
     * Retrieves detailed product information for the given product ID.
     *
     * <p>Optionally returns a response containing signed image URLs when requested.
     *
     * @param id        the product identifier; must not be {@code null}
     * @param signedUrl whether to include signed URLs in the response
     * @return a {@link ProductResponse} containing full product details
     * @throws ProductNotFoundExceptions if no product exists for the given ID
     */
    ProductResponse getProductById(
            final Integer id,
            final boolean signedUrl
    ) throws ProductNotFoundExceptions;


    /**
     * Processes purchase operations for one or more products.
     *
     * <p>This method performs a transactional purchase operation by validating
     * product availability, decrementing stock quantities, and returning the
     * purchase results for all requested products.</p>
     *
     * <p>This is a <b>command-style</b> operation (state-changing), not a query.
     * The result size is bounded by the input request, so pagination is intentionally
     * not applied.</p>
     *
     * <p>The entire operation executes within a single transaction. If any product
     * in the request does not exist or does not have sufficient stock, the transaction
     * is rolled back and no inventory changes are persisted.</p>
     *
     * @param request list of product purchase requests; must not be {@code null} or empty
     * @return a list of {@link ProductPurchaseResponse} representing the successfully
     *         purchased products
     * @throws ProductNotFoundExceptions if any product is missing or has insufficient stock
     */
    List<ProductPurchaseResponse> purchaseProducts(
            final List<ProductPurchaseRequest> request
    ) throws ProductNotFoundExceptions;


    /**
     * Retrieves products created between the specified start and end timestamps.
     *
     * <p>Either parameter may be {@code null}, in which case the service applies
     * flexible range behavior (e.g., "all before", "all after", or "all between").
     *
     * @param fromDate the starting timestamp (inclusive); may be {@code null}
     * @param toDate   the ending timestamp (inclusive); may be {@code null}
     * @param page     page index starting from 1
     * @param size     number of items per page
     * @return a paginated list of {@link ProductResponse} objects
     */
    PagedResponse<ProductResponse> findAllProducts(
            final LocalDateTime fromDate,
            final LocalDateTime toDate,
            final Integer page,
            final Integer size
    );

    /**
     * Retrieves products belonging to the specified category and filtered by price,
     * either greater-than-or-equal or less-than-or-equal depending on the given direction.
     *
     * <p>This method supports flexible price filtering, enabling:
     * <ul>
     *     <li>Price ≤ X searches (using {@code Direction.LE})</li>
     *     <li>Price ≥ X searches (using {@code Direction.GE})</li>
     * </ul>
     *
     * @param categoryId the category identifier; must not be {@code null}
     * @param price      the price value used for comparison
     * @param direction  the comparison direction ({@code LE} or {@code GE})
     * @param page       page index starting from 1
     * @param size       number of items per page
     * @return a paginated list of {@link ProductResponse} objects matching the filter
     * @throws CategoryNotFoundExceptions if the category does not exist
     */
    PagedResponse<ProductResponse> findAllProductsByCategory(
            final Integer categoryId,
            final BigDecimal price,
            final Direction direction,
            final Integer page,
            final Integer size
    ) throws CategoryNotFoundExceptions;
}