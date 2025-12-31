package com.forsaken.ecommerce.product.service;

import com.forsaken.ecommerce.common.exceptions.ProductNotFoundExceptions;
import com.forsaken.ecommerce.common.responses.PagedResponse;
import com.forsaken.ecommerce.product.configs.general.ProductProperties;
import com.forsaken.ecommerce.product.dto.ProductPurchaseRequest;
import com.forsaken.ecommerce.product.dto.ProductPurchaseResponse;
import com.forsaken.ecommerce.product.dto.ProductRequest;
import com.forsaken.ecommerce.product.dto.ProductResponse;
import com.forsaken.ecommerce.product.exceptions.CategoryNotFoundExceptions;
import com.forsaken.ecommerce.product.model.Category;
import com.forsaken.ecommerce.product.model.Product;
import com.forsaken.ecommerce.product.repository.ICategoryRepository;
import com.forsaken.ecommerce.product.repository.IProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static com.forsaken.ecommerce.product.dto.ProductRequest.Direction;
import static com.forsaken.ecommerce.product.dto.ProductRequest.Direction.GE;


@Service
@Slf4j
@RequiredArgsConstructor
public class ProductServiceImpl implements IProductService {

    private final IProductRepository repository;
    private final ICategoryRepository categoryRepository;
    private final IS3Service s3Service;
    private final ProductProperties productProperties;
    private final Class<?> className = ProductServiceImpl.class;

    @Override
    public Integer createProduct(final ProductRequest request) {
        log.info("Received request to create product {}", request);
        final Product product = request.toProduct();
        return repository.save(product).getId();
    }

    @Override
    public PagedResponse<ProductResponse> getAllProducts(
            final Boolean signedUrls,
            final Integer page,
            final Integer size
    ) {
        final int finalPage = page != null
                ? Math.max(page - 1, 0)
                : productProperties.defaultPageNumber();
        final int finalSize = size != null
                ? Math.min(Math.max(size, 1), productProperties.maxPageSize())
                : productProperties.defaultPageSize();
        final Pageable pageable = PageRequest.of(
                finalPage,
                finalSize
        );
        final Page<Product> productPage = repository.findAllWithCategory(pageable);
        if (signedUrls && productPage.hasContent())
            productPage.forEach(p -> p.setImageUrl(s3Service.generatePresignedDownloadUrl(p.getImageUrl())));

        return PagedResponse.<ProductResponse>builder()
                .content(
                        productPage.getContent()
                                .stream()
                                .map(Product::toProductResponse)
                                .toList()
                )
                .page(finalPage + 1)
                .size(productPage.getSize())
                .totalElements(productPage.getTotalElements())
                .totalPages(productPage.getTotalPages())
                .build();
    }

    @Override
    public ProductResponse getProductById(final Integer id, final boolean signedUrl) throws ProductNotFoundExceptions {
        log.info("Received request to get product by ID {}", id);
        final Optional<Product> productOpt = repository.findById(id);
        if (productOpt.isPresent() && signedUrl) {
            Product p = productOpt.get();
            p.setImageUrl(s3Service.generatePresignedDownloadUrl(p.getImageUrl()));
        }
        return productOpt.map(Product::toProductResponse)
                .orElseThrow(() -> new ProductNotFoundExceptions("Product not found with ID:: " + id,
                        "getProductById(final Integer id, final boolean signedUrl) in " + className));
    }

    @Override
    @Transactional(rollbackFor = ProductNotFoundExceptions.class)
    public List<ProductPurchaseResponse> purchaseProducts(
            final List<ProductPurchaseRequest> request
    ) throws ProductNotFoundExceptions {

        log.info("Received request to purchase products {}", request);
        final var productIds = request
                .stream()
                .map(ProductPurchaseRequest::productId)
                .toList();
        final var storedProducts = repository.findAllByIdInOrderById(productIds);
        if (productIds.size() != storedProducts.size()) {
            throw new ProductNotFoundExceptions("One or more products does not exist",
                    "purchaseProducts(List<ProductPurchaseRequest> request) in " + className);
        }
        final var sortedRequest = request
                .stream()
                .sorted(Comparator.comparing(ProductPurchaseRequest::productId))
                .toList();
        final var purchasedProducts = new ArrayList<ProductPurchaseResponse>();
        for (int i = 0; i < storedProducts.size(); i++) {
            final var product = storedProducts.get(i);
            final var productRequest = sortedRequest.get(i);
            if (product.getAvailableQuantity() < productRequest.quantity()) {
                throw new ProductNotFoundExceptions("Insufficient stock quantity for product with ID:: " + productRequest.productId(),
                        "purchaseProducts(List<ProductPurchaseRequest> request) in " + className);
            }
            final var newAvailableQuantity = product.getAvailableQuantity() - productRequest.quantity();
            product.setAvailableQuantity(newAvailableQuantity);
            repository.save(product);
            purchasedProducts.add(product.toproductPurchaseResponse(productRequest.quantity()));
        }
        return purchasedProducts;
    }

    @Override
    public PagedResponse<ProductResponse> findAllProducts(
            LocalDateTime fromDate,
            LocalDateTime toDate,
            final Integer page,
            final Integer size
    ) {
        log.info("Received request to get all products by date {} to {}", fromDate, toDate);

        if (toDate == null) toDate = LocalDateTime.now();
        if (fromDate == null) fromDate = toDate.minusMonths(6);

        final int finalPage = page != null
                ? Math.max(page - 1, 0)
                : productProperties.defaultPageNumber();
        final int finalSize = size != null
                ? Math.min(Math.max(size, 1), productProperties.maxPageSize())
                : productProperties.defaultPageSize();
        final Pageable pageable = PageRequest.of(
                finalPage,
                finalSize
        );
        final Page<Product> productPage = repository.findAllByAdditionDateBetween(fromDate, toDate, pageable);
        return PagedResponse.<ProductResponse>builder()
                .content(
                        productPage.getContent()
                                .stream().map(Product::toProductResponse)
                                .toList()
                )
                .page(finalPage + 1)
                .size(productPage.getSize())
                .totalElements(productPage.getTotalElements())
                .totalPages(productPage.getTotalPages())
                .isLastPage(productPage.isLast())
                .build();
    }

    @Override
    public PagedResponse<ProductResponse> findAllProductsByCategory(
            final Integer categoryId,
            final BigDecimal price,
            final Direction direction,
            final Integer page,
            final Integer size
    ) throws CategoryNotFoundExceptions {
        log.info("Received request to get all products by category {}", categoryId);
        final int finalPage = page != null
                ? Math.max(page - 1, 0)
                : productProperties.defaultPageNumber();
        final int finalSize = size != null
                ? Math.min(Math.max(size, 1), productProperties.maxPageSize())
                : productProperties.defaultPageSize();
        final Pageable pageable = PageRequest.of(
                finalPage,
                finalSize
        );

        final Category category = categoryRepository.findById(categoryId)
                .orElseThrow(
                        () -> new CategoryNotFoundExceptions(
                                "No Category found with ID: " + categoryId,
                                "findAllProductsByCategory(Integer categoryId,BigDecimal price," +
                                        "Direction direction,int page,int size) in " + className)
                );
        Page<Product> productPage = null;
        if (GE.equals(direction))
            productPage = repository.findAllByCategoryAndPriceGreaterThanEqual(category, price, pageable);
        else productPage = repository.findAllByCategoryAndPriceLessThanEqual(category, price, pageable);
        return PagedResponse.<ProductResponse>builder()
                .content(
                        productPage.getContent().stream().map(product -> product.toProductResponse()).toList()
                )
                .page(finalPage + 1)
                .size(productPage.getSize())
                .totalElements(productPage.getTotalElements())
                .totalPages(productPage.getTotalPages())
                .isLastPage(productPage.isLast())
                .build();
    }
}
