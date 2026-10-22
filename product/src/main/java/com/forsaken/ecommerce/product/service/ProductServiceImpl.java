package com.forsaken.ecommerce.product.service;

import com.forsaken.ecommerce.common.exceptions.ProductNotFoundExceptions;
import com.forsaken.ecommerce.common.responses.PagedResponse;
import com.forsaken.ecommerce.product.configs.general.ProductProperties;
import com.forsaken.ecommerce.product.dto.ProductPurchaseRequest;
import com.forsaken.ecommerce.product.dto.ProductPurchaseResponse;
import com.forsaken.ecommerce.product.dto.ProductReservationRequest;
import com.forsaken.ecommerce.product.dto.ProductRequest;
import com.forsaken.ecommerce.product.dto.ProductResponse;
import com.forsaken.ecommerce.product.exceptions.CategoryNotFoundExceptions;
import com.forsaken.ecommerce.product.model.Category;
import com.forsaken.ecommerce.product.model.Product;
import com.forsaken.ecommerce.product.model.ProductReservation;
import com.forsaken.ecommerce.product.model.ReservationStatus;
import com.forsaken.ecommerce.product.model.ReservationOrder;
import com.forsaken.ecommerce.product.repository.IReservationOrderRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;
import com.forsaken.ecommerce.product.repository.ICategoryRepository;
import com.forsaken.ecommerce.product.repository.IProductReservationRepository;
import com.forsaken.ecommerce.product.repository.IProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.forsaken.ecommerce.product.dto.ProductRequest.Direction;
import static com.forsaken.ecommerce.product.dto.ProductRequest.Direction.GE;


@Service
@Slf4j
@RequiredArgsConstructor
public class ProductServiceImpl implements IProductService {

    private final IProductRepository repository;
    private final ICategoryRepository categoryRepository;
    private final IProductReservationRepository reservationRepository;
    private final IReservationOrderRepository reservationOrderRepository;
    private final TransactionTemplate transactionTemplate;
    private final IS3Service s3Service;
    private final ProductProperties productProperties;
    private final Class<?> className = ProductServiceImpl.class;
    private static final int MAX_CONFLICT_ATTEMPTS = 3;

    @Override
    @CacheEvict(value = {"products", "productById"}, allEntries = true)
    public Integer createProduct(final ProductRequest request) {
        log.info("Received request to create product {}", request);
        final Product product = request.toProduct();
        return repository.save(product).getId();
    }

    @Override
    @Cacheable(
            value = "products",
            key = "{#page, #size}"
    )
    public PagedResponse<ProductResponse> getAllProducts(
            final Boolean signedUrls,
            final Integer page,
            final Integer size
    ) {
        final int finalPage = null != page
                ? Math.max(page - 1, 0)
                : productProperties.defaultPageNumber();
        final int finalSize = null != size
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
    @Cacheable(
            value = "productById",
            key = "#id",
            condition = "!#signedUrl"
    )
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
    @Caching(evict = {
            @CacheEvict(value = "productById", allEntries = true),
            @CacheEvict(value = "products", allEntries = true)
    })
    public List<ProductPurchaseResponse> purchaseProducts(
            final List<ProductPurchaseRequest> request
    ) throws ProductNotFoundExceptions {

        log.info("Received request to purchase products {}", request);
        if (null == request || request.isEmpty()) return List.of();

        final var productIds = request
                .stream()
                .map(ProductPurchaseRequest::productId)
                .toList();
        final var storedProducts = repository.findAllByIdInOrderByIdForUpdate(productIds);
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
    @Caching(evict = {
            @CacheEvict(value = "productById", allEntries = true),
            @CacheEvict(value = "products", allEntries = true)
    })
    public List<ProductPurchaseResponse> reserveProducts(final ProductReservationRequest request)
            throws ProductNotFoundExceptions {
        return executeWithConflictRetry(() -> reserveInTransaction(request));
    }

    private List<ProductPurchaseResponse> reserveInTransaction(final ProductReservationRequest request)
            throws ProductNotFoundExceptions {
        final ReservationOrder reservationOrder = lockOrCreateReservationOrder(request.orderReference());
        if (reservationOrder.getStatus() == ReservationStatus.RELEASED) {
            throw new ProductNotFoundExceptions(
                    "Order reference already has a released inventory reservation"
            );
        }
        final Map<Integer, Double> requestedQuantities = new LinkedHashMap<>();
        request.products().forEach(item -> {
            if (item.quantity() <= 0) {
                throw new IllegalArgumentException("Reservation quantities must be positive");
            }
            requestedQuantities.merge(item.productId(), item.quantity(), Double::sum);
        });

        final List<ProductReservation> existing =
                reservationRepository.findAllByOrderReferenceOrderByProductId(request.orderReference());
        if (!existing.isEmpty()) {
            final boolean sameRequest = existing.size() == requestedQuantities.size()
                    && existing.stream().allMatch(reservation ->
                    requestedQuantities.containsKey(reservation.getProductId())
                            && Double.compare(requestedQuantities.get(reservation.getProductId()),
                            reservation.getQuantity()) == 0);
            if (!sameRequest || existing.stream().anyMatch(
                    reservation -> reservation.getStatus() != ReservationStatus.RESERVED)) {
                throw new ProductNotFoundExceptions(
                        "Order reference already has a different or released inventory reservation"
                );
            }
            return existing.stream()
                    .map(reservation -> new ProductPurchaseResponse(
                            reservation.getProductId(),
                            reservation.getName(),
                            reservation.getDescription(),
                            reservation.getPrice(),
                            reservation.getQuantity()
                    ))
                    .toList();
        }

        final List<Integer> productIds = requestedQuantities.keySet().stream().sorted().toList();
        final List<Product> products = repository.findAllByIdInOrderByIdForUpdate(productIds);
        if (products.size() != productIds.size()) {
            throw new ProductNotFoundExceptions("One or more products does not exist");
        }

        for (final Product product : products) {
            final double quantity = requestedQuantities.get(product.getId());
            if (product.getAvailableQuantity() < quantity) {
                throw new ProductNotFoundExceptions(
                        "Insufficient stock quantity for product with ID:: " + product.getId()
                );
            }
        }

        final List<ProductReservation> reservations = products.stream()
                .map(product -> {
                    final double quantity = requestedQuantities.get(product.getId());
                    product.setAvailableQuantity(product.getAvailableQuantity() - quantity);
                    return new ProductReservation(request.orderReference(), product, quantity);
                })
                .toList();
        repository.saveAll(products);
        reservationRepository.saveAll(reservations);
        return reservations.stream()
                .map(reservation -> new ProductPurchaseResponse(
                        reservation.getProductId(),
                        reservation.getName(),
                        reservation.getDescription(),
                        reservation.getPrice(),
                        reservation.getQuantity()
                ))
                .toList();
    }

    @Override
    @Caching(evict = {
            @CacheEvict(value = "productById", allEntries = true),
            @CacheEvict(value = "products", allEntries = true)
    })
    public void releaseReservation(final String orderReference) {
        try {
            executeWithConflictRetry(() -> {
                releaseInTransaction(orderReference);
                return null;
            });
        } catch (ProductNotFoundExceptions impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private void releaseInTransaction(final String orderReference) {
        final ReservationOrder reservationOrder = lockOrCreateReservationOrder(orderReference);
        reservationOrder.release();
        reservationOrderRepository.save(reservationOrder);
        final List<ProductReservation> reservations =
                reservationRepository.findAllByOrderReferenceOrderByProductId(orderReference);
        final List<ProductReservation> active = reservations.stream()
                .filter(reservation -> reservation.getStatus() == ReservationStatus.RESERVED)
                .toList();
        if (active.isEmpty()) {
            return;
        }
        final List<Integer> productIds = active.stream()
                .map(ProductReservation::getProductId)
                .toList();
        final List<Product> products = repository.findAllByIdInOrderByIdForUpdate(productIds);
        if (products.size() != active.size()) {
            throw new IllegalStateException(
                    "Cannot release inventory: a reserved product is missing for order " + orderReference
            );
        }
        final Map<Integer, Product> productsById = products.stream()
                .collect(java.util.stream.Collectors.toMap(Product::getId, product -> product));
        active.forEach(reservation -> {
            final Product product = productsById.get(reservation.getProductId());
            product.setAvailableQuantity(product.getAvailableQuantity() + reservation.getQuantity());
            reservation.release();
        });
        repository.saveAll(products);
        reservationRepository.saveAll(active);
    }

    private ReservationOrder lockOrCreateReservationOrder(final String orderReference) {
        return reservationOrderRepository.findByOrderReferenceForUpdate(orderReference)
                .orElseGet(() -> reservationOrderRepository.saveAndFlush(
                        new ReservationOrder(orderReference, ReservationStatus.RESERVED)
                ));
    }

    /**
     * Runs the work in a transaction. A concurrent first insert of the same order reference
     * surfaces as a unique-key violation; the retry then finds and locks the winning record.
     */
    private <T> T executeWithConflictRetry(final ReservationWork<T> work) throws ProductNotFoundExceptions {
        for (int attempt = 1; ; attempt++) {
            try {
                return transactionTemplate.execute(status -> {
                    try {
                        return work.run();
                    } catch (ProductNotFoundExceptions ex) {
                        throw new ReservationRejectedException(ex);
                    }
                });
            } catch (ReservationRejectedException ex) {
                throw (ProductNotFoundExceptions) ex.getCause();
            } catch (DataIntegrityViolationException ex) {
                if (attempt >= MAX_CONFLICT_ATTEMPTS) {
                    throw ex;
                }
            }
        }
    }

    @FunctionalInterface
    private interface ReservationWork<T> {
        T run() throws ProductNotFoundExceptions;
    }

    private static final class ReservationRejectedException extends RuntimeException {
        private ReservationRejectedException(final ProductNotFoundExceptions cause) {
            super(cause);
        }
    }

    @Override
    @Cacheable(
            value = "productsByDate",
            key = "{#fromDate, #toDate, #page, #size}"
    )
    public PagedResponse<ProductResponse> findAllProducts(
            LocalDateTime fromDate,
            LocalDateTime toDate,
            final Integer page,
            final Integer size
    ) {
        log.info("Received request to get all products by date {} to {}", fromDate, toDate);

        if (toDate == null) toDate = LocalDateTime.now();
        if (fromDate == null) fromDate = toDate.minusMonths(6);

        final int finalPage = null != page
                ? Math.max(page - 1, 0)
                : productProperties.defaultPageNumber();
        final int finalSize = null != size
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
    @Cacheable(
            value = "productsByCategory",
            key = "{#categoryId, #price, #direction, #page, #size}"
    )
    public PagedResponse<ProductResponse> findAllProductsByCategory(
            final Integer categoryId,
            final BigDecimal price,
            final Direction direction,
            final Integer page,
            final Integer size
    ) throws CategoryNotFoundExceptions {
        log.info("Received request to get all products by category {}", categoryId);
        final int finalPage = null != page
                ? Math.max(page - 1, 0)
                : productProperties.defaultPageNumber();
        final int finalSize = null != size
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
        Page<Product> productPage;
        if (GE.equals(direction)) {
            productPage = repository.findAllByCategoryAndPriceGreaterThanEqual(category, price, pageable);
        } else {
            productPage = repository.findAllByCategoryAndPriceLessThanEqual(category, price, pageable);
        }
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