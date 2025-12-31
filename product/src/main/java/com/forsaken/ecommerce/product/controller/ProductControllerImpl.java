package com.forsaken.ecommerce.product.controller;

import com.forsaken.ecommerce.common.exceptions.ProductNotFoundExceptions;
import com.forsaken.ecommerce.common.responses.ApiResponse;
import com.forsaken.ecommerce.common.responses.PagedResponse;
import com.forsaken.ecommerce.product.dto.ProductPurchaseRequest;
import com.forsaken.ecommerce.product.dto.ProductPurchaseResponse;
import com.forsaken.ecommerce.product.dto.ProductRequest;
import com.forsaken.ecommerce.product.dto.ProductResponse;
import com.forsaken.ecommerce.product.exceptions.CategoryNotFoundExceptions;
import com.forsaken.ecommerce.product.service.IProductService;
import com.forsaken.ecommerce.product.service.IS3Service;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static com.forsaken.ecommerce.product.dto.ProductRequest.Direction;

@RestController
@RequiredArgsConstructor
public class ProductControllerImpl implements IProductController {

    private final IProductService service;
    private final IS3Service s3Service;

    @Override
    public ResponseEntity<ApiResponse<Map<String, String>>> getPresignedUrl(final String fileName, final String contentType) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                        ApiResponse.<Map<String, String>>builder()
                                .status(ApiResponse.Status.SUCCESS)
                                .data(s3Service.generatePresignedUploadUrl(fileName, contentType))
                                .message("Presigned Url Generated.")
                                .build()
                );
    }

    @Override
    public ResponseEntity<ApiResponse<Integer>> createProduct(final ProductRequest productRequest) throws IOException {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                        ApiResponse.<Integer>builder()
                                .status(ApiResponse.Status.SUCCESS)
                                .data(service.createProduct(productRequest))
                                .message("Product Created")
                                .build()
                );
    }

    @Override
    public ResponseEntity<ApiResponse<String>> getDownloadUrl(final String key) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                        ApiResponse.<String>builder()
                                .status(ApiResponse.Status.SUCCESS)
                                .data(s3Service.generatePresignedDownloadUrl(key))
                                .message("Presigned Url to download Product Image Generated.")
                                .build()
                );
    }

    @Override
    public ResponseEntity<ApiResponse<List<ProductPurchaseResponse>>> purchaseProducts(
            final List<ProductPurchaseRequest> request
    ) throws ProductNotFoundExceptions {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(
                        ApiResponse.<List<ProductPurchaseResponse>>builder()
                                .status(ApiResponse.Status.SUCCESS)
                                .data(service.purchaseProducts(request))
                                .message("Product Purchased.")
                                .build()
                );
    }

    @Override
    public ResponseEntity<ApiResponse<ProductResponse>> findById(
            final Integer productId,
            final Boolean signedUrl
    ) throws ProductNotFoundExceptions {
        return ResponseEntity.status(HttpStatus.OK)
                .body(
                        ApiResponse.<ProductResponse>builder()
                                .status(ApiResponse.Status.SUCCESS)
                                .data(service.getProductById(productId, signedUrl))
                                .message("Product Found with Id: " + productId)
                                .build()
                );
    }

    @Override
    public ResponseEntity<ApiResponse<PagedResponse<ProductResponse>>> findAll(
            final Boolean signedUrl,
            final Integer page,
            final Integer size
    ) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(
                        ApiResponse.<PagedResponse<ProductResponse>>builder()
                                .status(ApiResponse.Status.SUCCESS)
                                .data(service.getAllProducts(signedUrl, page, size))
                                .message("Fetched All Products Information.")
                                .build()
                );
    }

    @Override
    public ResponseEntity<ApiResponse<PagedResponse<ProductResponse>>> findAllProducts(final LocalDateTime fromDate,
                                                                                       final LocalDateTime toDate,
                                                                                       final Integer page,
                                                                                       final Integer size
    ) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(
                        ApiResponse.<PagedResponse<ProductResponse>>builder()
                                .status(ApiResponse.Status.SUCCESS)
                                .data(service.findAllProducts(fromDate, toDate, page, size))
                                .message("Fetched All Products Information created between fromDate to toDate.")
                                .build()
                );
    }

    @Override
    public ResponseEntity<ApiResponse<PagedResponse<ProductResponse>>> findAllProductsByCategory(
            final Integer categoryId,
            final BigDecimal price,
            final Direction direction,
            final Integer page,
            final Integer size
    ) throws CategoryNotFoundExceptions {
        return ResponseEntity.status(HttpStatus.OK)
                .body(
                        ApiResponse.<PagedResponse<ProductResponse>>builder()
                                .status(ApiResponse.Status.SUCCESS)
                                .data(service.findAllProductsByCategory(categoryId, price, direction, page, size))
                                .message("Fetched All Products By Category:category.")
                                .build()
                );
    }
}
