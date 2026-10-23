package com.forsaken.ecommerce.order.order.service;

import com.forsaken.ecommerce.avro.OrderConfirmation;
import com.forsaken.ecommerce.avro.PaymentMethod;
import com.forsaken.ecommerce.common.exceptions.BusinessException;
import com.forsaken.ecommerce.common.exceptions.CustomerNotFoundExceptions;
import com.forsaken.ecommerce.common.exceptions.PaymentFailedExceptions;
import com.forsaken.ecommerce.common.exceptions.ProductNotFoundExceptions;
import com.forsaken.ecommerce.common.responses.PagedResponse;
import com.forsaken.ecommerce.order.configs.general.OrderProperties;
import com.forsaken.ecommerce.order.customer.CustomerResponse;
import com.forsaken.ecommerce.order.customer.ICustomerService;
import com.forsaken.ecommerce.order.order.dto.OrderRequest;
import com.forsaken.ecommerce.order.order.dto.OrderResponse;
import com.forsaken.ecommerce.order.order.model.Order;
import com.forsaken.ecommerce.order.order.model.OrderSagaStatus;
import com.forsaken.ecommerce.order.order.repository.IOrderRepository;
import com.forsaken.ecommerce.order.orderline.model.OrderLine;
import com.forsaken.ecommerce.order.payment.IPaymentService;
import com.forsaken.ecommerce.order.payment.PaymentRequest;
import com.forsaken.ecommerce.order.product.IProductService;
import com.forsaken.ecommerce.order.product.PurchaseRequest;
import com.forsaken.ecommerce.order.product.PurchaseResponse;
import io.micrometer.tracing.Tracer;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.Conversions;
import org.apache.avro.LogicalTypes;
import org.apache.avro.Schema;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionException;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements IOrderService, IOrderSagaRecoveryService {

    private static final Duration CLAIM_TTL = Duration.ofMinutes(5);

    private final IOrderRepository orderRepository;
    private final ICustomerService customerService;
    private final IProductService productService;
    private final IPaymentService paymentService;
    private final IOrderProducer orderProducer;
    private final OrderProperties orderProperties;
    private final OrderCacheEvictor orderCacheEvictor;
    private final Tracer tracer;

    @Override
    public Integer createOrder(final OrderRequest request)
            throws CustomerNotFoundExceptions, BusinessException,
            PaymentFailedExceptions, ProductNotFoundExceptions {
        log.info("Creating Order Request: {}", request);
        final CustomerResponse customer;
        try {
            customer = customerService.getCustomer(request.customerId()).join();
        } catch (CompletionException ex) {
            final Throwable cause = ex.getCause();
            if (cause instanceof CustomerNotFoundExceptions e) {
                throw e;
            }
            throw new CustomerNotFoundExceptions(
                    "Customer lookup failed",
                    "createOrder(OrderRequest) in " + getClass().getSimpleName(),
                    cause == null ? ex : cause
            );
        }

        final String owner = newClaimOwner();
        final Order order = request.toOrder();
        order.setClaimOwner(owner);
        order.setClaimExpiresAt(LocalDateTime.now().plus(CLAIM_TTL));
        request.products().forEach(p -> {
            OrderLine orderLine = OrderLine.builder()
                    .productId(p.productId())
                    .quantity(p.quantity())
                    .build();

            order.addOrderLine(orderLine);
        });
        order.setCustomerFirstname(customer.firstname());
        order.setCustomerLastname(customer.lastname());
        order.setCustomerEmail(customer.email());
        final Order savedOrder = orderRepository.saveAndFlush(order);
        try {
            executeSaga(savedOrder, owner, customer, request.products());
            return savedOrder.getId();
        } finally {
            orderCacheEvictor.evictAll();
        }
    }

    private void executeSaga(
            final Order order,
            final String owner,
            final CustomerResponse customer,
            final List<PurchaseRequest> products
    ) throws CustomerNotFoundExceptions, BusinessException,
            PaymentFailedExceptions, ProductNotFoundExceptions {
        final List<PurchaseResponse> reservedProducts;
        try {
            reservedProducts = productService.reserveProducts(order.getReference(), products);
            reservedProducts.forEach(this::toAvroPurchase);
            paymentService.pay(PaymentRequest.builder()
                    .amount(order.getTotalAmount())
                    .paymentMethod(order.getPaymentMethod())
                    .orderId(order.getId())
                    .orderReference(order.getReference())
                    .customer(customer)
                    .build());
            // Durable confirmation intent: from here on the saga only moves forward.
            advance(order, owner, OrderSagaStatus.PENDING, OrderSagaStatus.CONFIRMING);
        } catch (Exception failure) {
            failSaga(order, owner, failure);
            return;
        }
        publishAndComplete(order, owner, customer, reservedProducts);
    }

    /**
     * Compensates a saga that failed before payment was confirmed and the confirmation phase began.
     * Compensation only starts if this worker still owns the saga and it is still PENDING.
     */
    private void failSaga(final Order order, final String owner, final Exception failure)
            throws CustomerNotFoundExceptions, BusinessException,
            PaymentFailedExceptions, ProductNotFoundExceptions {
        log.error("Order saga failed for reference={}; starting compensation",
                order.getReference(), failure);
        try {
            advance(order, owner, OrderSagaStatus.PENDING, OrderSagaStatus.COMPENSATION_REQUIRED);
        } catch (OrderOwnershipLostException lost) {
            lost.addSuppressed(failure);
            throw sagaBusinessException(
                    "Order saga lost ownership of order " + order.getReference() + "; no compensation was run",
                    lost
            );
        }
        final Exception compensationFailure = compensate(order);
        if (compensationFailure == null) {
            try {
                advance(order, owner, OrderSagaStatus.COMPENSATION_REQUIRED, OrderSagaStatus.FAILED);
            } catch (OrderOwnershipLostException lost) {
                log.warn("Ownership lost while finishing compensation for order reference={}",
                        order.getReference());
            }
            rethrowSagaFailure(failure);
        }
        failure.addSuppressed(compensationFailure);
        orderRepository.releaseClaim(order.getId(), owner);
        log.error("Compensation is incomplete for order reference={}; manual recovery is required",
                order.getReference(), compensationFailure);
        throw sagaBusinessException(
                "Order saga failed and compensation requires recovery for order " + order.getReference(),
                failure
        );
    }

    /**
     * Forward-only phase: payment is confirmed, so failures here are retried and never compensated.
     */
    private void publishAndComplete(
            final Order order,
            final String owner,
            final CustomerResponse customer,
            final List<PurchaseResponse> products
    ) throws BusinessException {
        try {
            // Idempotent; an uncertain earlier outcome (e.g. a lost response) is reconciled here.
            paymentService.confirm(order.getId());
            publishOrderConfirmation(order, customer, products);
            advance(order, owner, OrderSagaStatus.CONFIRMING, OrderSagaStatus.COMPLETED);
        } catch (Exception failure) {
            log.error("Order confirmation for reference={} is not complete; it will be retried",
                    order.getReference(), failure);
            orderRepository.releaseClaim(order.getId(), owner);
            throw sagaBusinessException(
                    "Order " + order.getReference() + " is paid; confirmation publication will be retried",
                    failure
            );
        }
    }

    private void advance(
            final Order order,
            final String owner,
            final OrderSagaStatus from,
            final OrderSagaStatus to
    ) {
        if (orderRepository.transition(order.getId(), owner, LocalDateTime.now(), from, to) == 0) {
            throw new OrderOwnershipLostException(order.getReference(), from, to);
        }
        order.setSagaStatus(to);
    }

    private BusinessException sagaBusinessException(final String message, final Throwable cause) {
        return new BusinessException(
                message,
                "createOrder(OrderRequest) in " + getClass().getSimpleName(),
                cause
        );
    }

    private Exception compensate(final Order order) {
        Exception compensationFailure = null;
        try {
            paymentService.cancel(order.getId());
        } catch (Exception ex) {
            compensationFailure = ex;
        }
        try {
            productService.releaseReservation(order.getReference());
        } catch (Exception ex) {
            if (compensationFailure == null) {
                compensationFailure = ex;
            } else {
                compensationFailure.addSuppressed(ex);
            }
        }
        return compensationFailure;
    }

    @Override
    public SagaRecoveryOutcome retryCompensation(final Integer orderId) {
        final Order candidate = orderRepository.findById(orderId).orElse(null);
        if (candidate == null) {
            return SagaRecoveryOutcome.NOT_CLAIMED;
        }
        final String owner = newClaimOwner();
        if (!claim(candidate, owner, OrderSagaStatus.COMPENSATION_PENDING)) {
            return SagaRecoveryOutcome.NOT_CLAIMED;
        }
        try {
            final Exception compensationFailure = compensate(candidate);
            if (compensationFailure != null) {
                orderRepository.releaseClaim(candidate.getId(), owner);
                throw new SagaRecoveryException(
                        "Saga compensation retry failed for order reference=" + candidate.getReference(),
                        compensationFailure
                );
            }
            advance(candidate, owner, OrderSagaStatus.COMPENSATION_REQUIRED, OrderSagaStatus.FAILED);
            log.info("Saga compensation completed for order reference={}", candidate.getReference());
            return SagaRecoveryOutcome.RECOVERED;
        } catch (SagaRecoveryException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new SagaRecoveryException(
                    "Saga compensation retry aborted for order reference=" + candidate.getReference(), ex);
        } finally {
            orderCacheEvictor.evictAll();
        }
    }

    @Override
    public SagaRecoveryOutcome recover(final Integer orderId) {
        final Order candidate = orderRepository.findById(orderId).orElse(null);
        if (candidate == null) {
            return SagaRecoveryOutcome.NOT_CLAIMED;
        }
        final String owner = newClaimOwner();
        if (!claim(candidate, owner, OrderSagaStatus.RECOVERABLE)) {
            return SagaRecoveryOutcome.NOT_CLAIMED;
        }
        try {
            final Order order = orderRepository.findDetailedById(candidate.getId()).orElseThrow();
            return resume(order, owner);
        } catch (Exception ex) {
            throw new SagaRecoveryException(
                    "Unable to complete recovery of order saga for reference=" + candidate.getReference(), ex);
        } finally {
            orderCacheEvictor.evictAll();
        }
    }

    private SagaRecoveryOutcome resume(final Order order, final String owner)
            throws CustomerNotFoundExceptions, BusinessException,
            PaymentFailedExceptions, ProductNotFoundExceptions {
        final List<PurchaseRequest> requestedProducts = order.getOrderLines().stream()
                .map(line -> new PurchaseRequest(line.getProductId(), line.getQuantity()))
                .toList();
        final CustomerResponse customer;
        try {
            customer = customerFor(order);
        } catch (Exception failure) {
            if (order.getSagaStatus() == OrderSagaStatus.PENDING) {
                failSaga(order, owner, failure);
            } else {
                orderRepository.releaseClaim(order.getId(), owner);
                return SagaRecoveryOutcome.DEFERRED;
            }
            return SagaRecoveryOutcome.RECOVERED;
        }
        if (order.getSagaStatus() == OrderSagaStatus.PENDING) {
            executeSaga(order, owner, customer, requestedProducts);
            return SagaRecoveryOutcome.RECOVERED;
        }
        final List<PurchaseResponse> reservedProducts;
        try {
            reservedProducts = productService.reserveProducts(order.getReference(), requestedProducts);
        } catch (Exception failure) {
            orderRepository.releaseClaim(order.getId(), owner);
            throw sagaBusinessException("Unable to load reservation snapshot for " + order.getReference(), failure);
        }
        publishAndComplete(order, owner, customer, reservedProducts);
        log.info("Recovered order saga for reference={}", order.getReference());
        return SagaRecoveryOutcome.RECOVERED;
    }

    /** Uses the snapshot captured at creation so recovery survives customer deletion. */
    private CustomerResponse customerFor(final Order order) {
        if (order.getCustomerEmail() != null) {
            return new CustomerResponse(
                    order.getCustomerId(),
                    order.getCustomerFirstname(),
                    order.getCustomerLastname(),
                    order.getCustomerEmail()
            );
        }
        return customerService.getCustomer(order.getCustomerId()).join();
    }

    private boolean claim(final Order order, final String owner, final List<OrderSagaStatus> statuses) {
        final LocalDateTime now = LocalDateTime.now();
        return orderRepository.claim(order.getId(), owner, now, now.plus(CLAIM_TTL), statuses) == 1;
    }

    private static String newClaimOwner() {
        return UUID.randomUUID().toString();
    }

    private static final class OrderOwnershipLostException extends RuntimeException {
        private OrderOwnershipLostException(
                final String reference,
                final OrderSagaStatus from,
                final OrderSagaStatus to
        ) {
            super("Saga ownership lost for order " + reference + " while moving " + from + " -> " + to);
        }
    }

    private void publishOrderConfirmation(
            final Order order,
            final CustomerResponse customer,
            final List<PurchaseResponse> products
    ) {
        final String traceId = (null != tracer && null != tracer.currentSpan())
                ? tracer.currentSpan().context().traceId()
                : "NO_TRACE";
        final OrderConfirmation orderConfirmation = OrderConfirmation.newBuilder()
                .setOrderReference(order.getReference())
                .setTotalAmount(convertBigDecimalToBytes(order.getTotalAmount()))
                .setPaymentMethod(PaymentMethod.valueOf(order.getPaymentMethod().name()))
                .setCustomer(toAvroCustomer(customer))
                .setProducts(products.stream().map(this::toAvroPurchase).toList())
                .setTraceId(traceId)
                .build();
        orderProducer.sendOrderConfirmation(orderConfirmation);
    }

    private void rethrowSagaFailure(final Exception failure)
            throws CustomerNotFoundExceptions, BusinessException,
            PaymentFailedExceptions, ProductNotFoundExceptions {
        if (failure instanceof CustomerNotFoundExceptions exception) throw exception;
        if (failure instanceof BusinessException exception) throw exception;
        if (failure instanceof PaymentFailedExceptions exception) throw exception;
        if (failure instanceof ProductNotFoundExceptions exception) throw exception;
        if (failure instanceof RuntimeException exception) throw exception;
        throw new BusinessException(
                "Order saga failed",
                "createOrder(OrderRequest) in " + getClass().getSimpleName(),
                failure
        );
    }

    @Override
    @Cacheable(
            value = "orders",
            key = "{#page, #size}"
    )
    public PagedResponse<OrderResponse> findAllOrders(final Integer page, final Integer size) {
        log.info("Finding all orders | page={}, size={}", page, size);
        final int finalPage = page != null
                ? Math.max(page - 1, 0)
                : orderProperties.defaultPageNumber();
        final int finalSize = size != null
                ? Math.min(Math.max(size, 1), orderProperties.maxPageSize())
                : orderProperties.defaultPageSize();
        final Pageable pageable = PageRequest.of(
                finalPage,
                finalSize,
                Sort.by(Sort.Direction.DESC, "createdDate")
        );
        final Page<Order> ordersPage = orderRepository.findAll(pageable);
        return PagedResponse.<OrderResponse>builder()
                .content(ordersPage.stream().map(Order::fromOrder).toList())
                .page(ordersPage.getNumber() + 1)
                .size(ordersPage.getSize())
                .totalElements(ordersPage.getTotalElements())
                .totalPages(ordersPage.getTotalPages())
                .isLastPage(ordersPage.isLast())
                .build();
    }

    @Override
    @Cacheable(value = "orderById", key = "#id")
    public OrderResponse findById(final Integer id) {
        log.info("Finding Order by ID: {}", id);
        return this.orderRepository.findById(id)
                .map(Order::fromOrder)
                .orElseThrow(() -> new EntityNotFoundException
                        (String.format("No order found with the provided ID: %d", id))
                );
    }

    @Override
    @Cacheable(
            value = "ordersByCustomer",
            key = "{#customerId, " +
                    "(#fromDate != null ? #fromDate.toString() : 'START'), " +
                    "(#toDate != null ? #toDate.toString() : 'NOW'), " +
                    "#page, #size}"
    )
    public PagedResponse<OrderResponse> findAllOrdersByCustomerId(
            final String customerId,
            final LocalDateTime fromDate,
            final LocalDateTime toDate,
            final Integer page,
            final Integer size
    ) {
        log.info("Finding All Orders By Customer: {}", customerId);
        final int finalPage = page != null
                ? Math.max(page - 1, 0)
                : orderProperties.defaultPageNumber();
        final int finalSize = size != null
                ? Math.min(Math.max(size, 1), orderProperties.maxPageSize())
                : orderProperties.defaultPageSize();

        final LocalDateTime finalToDate = toDate != null
                ? toDate
                : LocalDateTime.now();
        final LocalDateTime finalFromDate = fromDate != null
                ? fromDate
                : finalToDate.minusMonths(6);

        final Pageable pageable = PageRequest.of(
                finalPage,
                finalSize,
                Sort.by(Sort.Direction.DESC, "createdDate"));
        final Page<Order> orderPage =
                orderRepository.findAllByCustomerIdAndCreatedDateBetween(customerId, finalFromDate, finalToDate, pageable);

        return PagedResponse.<OrderResponse>builder()
                .content(orderPage.getContent().stream().map(Order::fromOrder).toList())
                .page(finalPage + 1)
                .size(orderPage.getSize())
                .totalElements(orderPage.getTotalElements())
                .totalPages(orderPage.getTotalPages())
                .isLastPage(orderPage.isLast())
                .build();
    }

    /**
     * Converts a {@link BigDecimal} monetary value into an Avro-compatible
     * {@link ByteBuffer} using the {@code decimal} logical type.
     *
     * <p>Avro does not natively support {@link BigDecimal}, so monetary values
     * must be encoded as {@code BYTES} with a {@code decimal} logical type
     * specifying precision and scale.</p>
     *
     * <p>This implementation uses:</p>
     * <ul>
     *   <li><b>Precision:</b> 18 digits</li>
     *   <li><b>Scale:</b> 2 decimal places</li>
     * </ul>
     *
     * <p>The resulting {@link ByteBuffer} is safe for serialization in Avro
     * messages and preserves exact numeric precision required for financial data.</p>
     *
     * @param value the monetary value to convert; may be {@code null}
     * @return a {@link ByteBuffer} containing the Avro-encoded decimal value,
     * or {@code null} if the input value is {@code null}
     */
    private ByteBuffer convertBigDecimalToBytes(final BigDecimal value) {
        if (value == null) {
            return null;
        }
        final Schema DECIMAL_SCHEMA =
                LogicalTypes.decimal(18, 2)
                        .addToSchema(Schema.create(Schema.Type.BYTES));

        final Conversions.DecimalConversion DECIMAL_CONVERSION =
                new Conversions.DecimalConversion();
        return DECIMAL_CONVERSION.toBytes(value, DECIMAL_SCHEMA, DECIMAL_SCHEMA.getLogicalType());
    }

    /**
     * Maps an internal {@link CustomerResponse} DTO into its corresponding
     * Avro representation used for inter-service communication.
     *
     * <p>This method performs a straightforward field-to-field mapping
     * without transformation or enrichment.</p>
     *
     * <p>The resulting Avro object is used as part of an
     * {@link com.forsaken.ecommerce.avro.OrderConfirmation} event.</p>
     *
     * @param customer the customer DTO obtained from the customer service
     * @return an Avro {@link com.forsaken.ecommerce.avro.CustomerResponse}
     * representing the same customer data
     */
    private com.forsaken.ecommerce.avro.CustomerResponse toAvroCustomer(final CustomerResponse customer) {
        return com.forsaken.ecommerce.avro.CustomerResponse.newBuilder()
                .setId(customer.id())
                .setFirstname(customer.firstname())
                .setLastname(customer.lastname())
                .setEmail(customer.email())
                .build();
    }

    /**
     * Converts a {@link PurchaseResponse} DTO into its Avro equivalent for
     * inclusion in order confirmation events.
     *
     * <p>Price values are converted using {@link #convertBigDecimalToBytes(BigDecimal)}
     * to ensure compatibility with Avro decimal logical types.</p>
     *
     * <p>This method is typically invoked when building an
     * {@link com.forsaken.ecommerce.avro.OrderConfirmation} payload.</p>
     *
     * @param product the purchased product DTO
     * @return an Avro {@link com.forsaken.ecommerce.avro.PurchaseResponse}
     * representing the purchased product
     */
    private com.forsaken.ecommerce.avro.PurchaseResponse toAvroPurchase(final PurchaseResponse product) {
        return com.forsaken.ecommerce.avro.PurchaseResponse.newBuilder()
                .setProductId(product.productId())
                .setName(product.name())
                .setDescription(product.description())
                .setPrice(convertBigDecimalToBytes(product.price()))
                .setQuantity(product.quantity())
                .build();
    }
}