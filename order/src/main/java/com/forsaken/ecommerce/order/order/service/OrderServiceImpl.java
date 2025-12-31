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
import com.forsaken.ecommerce.order.order.repository.IOrderRepository;
import com.forsaken.ecommerce.order.orderline.model.OrderLine;
import com.forsaken.ecommerce.order.payment.IPaymentService;
import com.forsaken.ecommerce.order.payment.PaymentRequest;
import com.forsaken.ecommerce.order.product.IProductService;
import com.forsaken.ecommerce.order.product.PurchaseResponse;
import io.micrometer.tracing.Tracer;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.Conversions;
import org.apache.avro.LogicalTypes;
import org.apache.avro.Schema;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements IOrderService {

    private final IOrderRepository orderRepository;
    private final ICustomerService customerService;
    private final IProductService productService;
    private final IPaymentService paymentService;
    private final IOrderProducer orderProducer;
    private final OrderProperties orderProperties;
    private final Tracer tracer;

    @Transactional(
            rollbackFor = {
                    CustomerNotFoundExceptions.class,
                    BusinessException.class,
                    PaymentFailedExceptions.class,
                    ProductNotFoundExceptions.class
            }
    )
    @Override
    public Integer createOrder(final OrderRequest request)
            throws CustomerNotFoundExceptions, BusinessException,
            PaymentFailedExceptions, ProductNotFoundExceptions {
        log.info("Creating Order Request: {}", request);
        final var fetchedCustomer = customerService.getCustomer(request.customerId());
        final var fetchedPurchasedProducts = productService.purchaseProducts(request.products());
        try {
            CompletableFuture.allOf(
                    fetchedCustomer,
                    fetchedPurchasedProducts
            ).join();
        } catch (CompletionException ex) {
            final Throwable cause = ex.getCause();

            if (cause instanceof CustomerNotFoundExceptions e) throw e;
            if (cause instanceof ProductNotFoundExceptions e) throw e;
            if (cause instanceof BusinessException e) throw e;

            log.error(
                    "Unexpected async failure during order creation. Cause type={}",
                    (cause != null ? cause.getClass().getName() : "null"),
                    ex
            );
            throw ex;
        }

        final var customer = fetchedCustomer.join();
        final var purchasedProducts = fetchedPurchasedProducts.join();
        final Order order = request.toOrder();
        request.products().forEach(p -> {
            OrderLine orderLine = OrderLine.builder()
                    .productId(p.productId())
                    .quantity(p.quantity())
                    .build();

            order.addOrderLine(orderLine);
        });
        final Order savedOrder = orderRepository.save(order);

        final PaymentRequest paymentRequest = PaymentRequest.builder()
                .amount(request.amount())
                .paymentMethod(request.paymentMethod())
                .orderId(savedOrder.getId())
                .orderReference(savedOrder.getReference())
                .customer(customer)
                .build();
        paymentService.pay(paymentRequest);
        log.info("Sent Payment");

        final String traceId = tracer.currentSpan() != null
                ? tracer.currentSpan().context().traceId()
                : "NO_TRACE";
        final OrderConfirmation orderConfirmation = OrderConfirmation.newBuilder()
                .setOrderReference(request.reference())
                .setTotalAmount(convertBigDecimalToBytes(request.amount()))
                .setPaymentMethod(PaymentMethod.valueOf(request.paymentMethod().name()))
                .setCustomer(toAvroCustomer(customer))
                .setProducts(purchasedProducts.stream().map(this::toAvroPurchase).toList())
                .setTraceId(traceId)
                .build();
        log.info("Created Order Confirmation: {}", orderConfirmation);
        orderProducer.sendOrderConfirmation(orderConfirmation);
        log.info("Sent Order Confirmation");
        return savedOrder.getId();
    }

    @Override
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
    public OrderResponse findById(final Integer id) {
        log.info("Finding Order by ID: {}", id);
        return this.orderRepository.findById(id)
                .map(Order::fromOrder)
                .orElseThrow(() -> new EntityNotFoundException
                        (String.format("No order found with the provided ID: %d", id))
                );
    }

    @Override
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

    private com.forsaken.ecommerce.avro.CustomerResponse toAvroCustomer(final CustomerResponse customer) {
        return com.forsaken.ecommerce.avro.CustomerResponse.newBuilder()
                .setId(customer.id())
                .setFirstname(customer.firstname())
                .setLastname(customer.lastname())
                .setEmail(customer.email())
                .build();
    }

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
