package com.forsaken.ecommerce.product.controller;

import com.forsaken.ecommerce.common.exceptions.ProductNotFoundExceptions;
import com.forsaken.ecommerce.common.responses.ApiResponse;
import com.forsaken.ecommerce.common.responses.PagedResponse;
import com.forsaken.ecommerce.product.dto.ProductPurchaseRequest;
import com.forsaken.ecommerce.product.dto.ProductPurchaseResponse;
import com.forsaken.ecommerce.product.dto.ProductRequest;
import com.forsaken.ecommerce.product.dto.ProductResponse;
import com.forsaken.ecommerce.product.exceptions.CategoryNotFoundExceptions;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static com.forsaken.ecommerce.product.dto.ProductRequest.Direction;

/**
 * REST controller interface for managing product-related operations such as product
 * creation, retrieval, purchasing, and generating presigned URLs for S3 interactions.
 *
 * <p>All endpoints are prefixed with <b>/api/v1/products</b> and return responses wrapped
 * inside {@link ApiResponse}, ensuring consistent structure across the API.
 */
@RequestMapping("/api/v1/products")
public interface IProductController {

    /**
     * Generates an S3 presigned URL for uploading a file.
     *
     * <p>This endpoint produces a temporary, secure upload URL that clients can use
     * to upload product images or related assets directly to Amazon S3, bypassing the backend.
     *
     * @param fileName    the name of the file to be uploaded; must not be blank
     * @param contentType the MIME type of the file (e.g., image/png); must not be blank
     * @return a {@link ResponseEntity} containing an {@link ApiResponse} with a map of key–URL pairs
     */
    @GetMapping("/presigned-url")
    ResponseEntity<ApiResponse<Map<String, String>>> getPresignedUrl(
            @RequestParam(name = "fileName") @NotBlank final String fileName,
            @RequestParam(name = "contentType") @NotBlank final String contentType
    );


    /**
     * Creates a new product in the system.
     *
     * <p>Accepts a {@link ProductRequest} payload and delegates product creation
     * to the service layer. On success, returns the generated product ID.
     *
     * @param productRequest the product creation request payload; must be valid
     * @return a {@link ResponseEntity} containing an {@link ApiResponse} with the product ID
     * @throws IOException if an I/O operation occurs while processing the product creation
     */
    @PostMapping(value = "/create")
    ResponseEntity<ApiResponse<Integer>> createProduct(
            @RequestBody @Valid final ProductRequest productRequest) throws IOException;


    /**
     * Generates an S3 presigned URL for downloading a file.
     *
     * <p>This URL allows clients to securely access protected S3 objects without exposing AWS credentials.
     *
     * @param key the S3 object key representing the file to be downloaded; must not be blank
     * @return a {@link ResponseEntity} containing an {@link ApiResponse} with the download URL
     */
    @GetMapping("/download-url")
    ResponseEntity<ApiResponse<String>> getDownloadUrl(@NotBlank @RequestParam(name = "key") final String key);


    /**
     * Purchases one or more products.
     *
     * <p>This endpoint performs a transactional purchase operation by validating
     * product availability, decrementing inventory quantities, and returning the
     * purchase results for all requested products.</p>
     *
     * <p>This is a <b>command-style</b> endpoint (state-changing). Pagination is
     * intentionally not applied because the response size is bounded by the
     * input request.</p>
     *
     * <p>If any product in the request does not exist or does not have sufficient
     * stock, the entire transaction is rolled back and no inventory updates
     * are persisted.</p>
     *
     * <p>An empty request is treated as a valid no-op and results in an empty list.</p>
     *
     * @param request list of product purchase requests; must not be {@code null}
     * @return a {@link ResponseEntity} containing an {@link ApiResponse} with a
     *         list of {@link ProductPurchaseResponse} items
     * @throws ProductNotFoundExceptions if any product is missing or has insufficient stock
     */
    @PostMapping("/purchase")
    ResponseEntity<ApiResponse<List<ProductPurchaseResponse>>> purchaseProducts(
            @RequestBody @Valid final List<ProductPurchaseRequest> request
    ) throws ProductNotFoundExceptions;


    /**
     * Retrieves product details by product ID.
     *
     * <p>Optionally returns signed URLs for the product's image if {@code signedUrl} is true.
     *
     * @param productId the ID of the product; must not be null
     * @param signedUrl whether to include signed URLs for product images
     * @return a {@link ResponseEntity} containing an {@link ApiResponse} with the product details
     * @throws ProductNotFoundExceptions if the product is not found
     */
    @GetMapping("/{product-id}")
    ResponseEntity<ApiResponse<ProductResponse>> findById(
            @PathVariable("product-id") @NotNull final Integer productId,
            @RequestParam(name = "signedUrl", defaultValue = "True") final Boolean signedUrl
    ) throws ProductNotFoundExceptions;


    /**
     * Retrieves a paginated list of all products.
     *
     * <p>Supports optional signed URL generation for product images.
     *
     * @param signedUrl whether to include signed URLs for product images
     * @param page      page index starting from 1
     * @param size      number of products per page
     * @return a {@link ResponseEntity} with an {@link ApiResponse} wrapping a page of product data
     */
    @GetMapping
    ResponseEntity<ApiResponse<PagedResponse<ProductResponse>>> findAll(
            @RequestParam(name = "signedUrl", defaultValue = "True") final Boolean signedUrl,
            @RequestParam(name = "page", required = false) final Integer page,
            @RequestParam(name = "size", required = false) final Integer size
    );


    /**
     * Retrieves all products created between the specified date range.
     *
     * <p>If {@code fromDate} or {@code toDate} is omitted, the service applies flexible filtering.
     *
     * @param fromDate the start of the creation date range; optional
     * @param toDate   the end of the creation date range; optional
     * @param page     page index starting from 1
     * @param size     number of products per page
     * @return a {@link ResponseEntity} with an {@link ApiResponse} containing a paginated list
     * of matching products
     */
    @GetMapping("/findAll")
    ResponseEntity<ApiResponse<PagedResponse<ProductResponse>>> findAllProducts(
            @RequestParam(name = "fromDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) final LocalDateTime fromDate,

            @RequestParam(name = "toDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) final LocalDateTime toDate,

            @RequestParam(name = "page", required = false) final Integer page,
            @RequestParam(name = "size", required = false) final Integer size
    );


    /**
     * Retrieves products filtered by category and price conditions.
     *
     * <p>The price filter supports direction-based comparison:
     * <ul>
     *     <li>{@code LE} – less than or equal to the price</li>
     *     <li>{@code GE} – greater than or equal to the price</li>
     * </ul>
     *
     * @param categoryId the category ID; must not be null
     * @param price      the comparison price value; defaults to 100
     * @param direction  comparison direction for price filtering; defaults to {@code GE}
     * @param page       page index starting from 1
     * @param size       items per page
     * @return a {@link ResponseEntity} containing an {@link ApiResponse} with paginated product data
     * @throws CategoryNotFoundExceptions if the category does not exist
     */
    @GetMapping("/category/{categoryId}")
    ResponseEntity<ApiResponse<PagedResponse<ProductResponse>>> findAllProductsByCategory(
            @PathVariable("categoryId") @NotNull final Integer categoryId,
            @RequestParam(value = "price", defaultValue = "100") final BigDecimal price,
            @RequestParam(value = "direction", defaultValue = "GE") final Direction direction,
            @RequestParam(name = "page", required = false) final Integer page,
            @RequestParam(name = "size", required = false) final Integer size
    ) throws CategoryNotFoundExceptions;
}
