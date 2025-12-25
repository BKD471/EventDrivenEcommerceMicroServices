package com.forsaken.ecommerce.order.order.service;

import com.forsaken.ecommerce.avro.OrderConfirmation;
import com.forsaken.ecommerce.avro.PaymentMethod;
import com.forsaken.ecommerce.common.exceptions.BusinessException;
import com.forsaken.ecommerce.common.exceptions.CustomerNotFoundExceptions;
import com.forsaken.ecommerce.common.exceptions.PaymentFailedExceptions;
import com.forsaken.ecommerce.common.exceptions.ProductNotFoundExceptions;
import com.forsaken.ecommerce.order.customer.CustomerResponse;
import com.forsaken.ecommerce.order.customer.ICustomerService;
import com.forsaken.ecommerce.order.kafka.IOrderProducer;
import com.forsaken.ecommerce.order.order.dto.OrderRequest;
import com.forsaken.ecommerce.order.order.dto.OrderResponse;
import com.forsaken.ecommerce.order.order.model.Order;
import com.forsaken.ecommerce.order.order.repository.IOrderRepository;
import com.forsaken.ecommerce.order.orderline.model.OrderLine;
import com.forsaken.ecommerce.order.payment.IPaymentService;
import com.forsaken.ecommerce.order.payment.PaymentRequest;
import com.forsaken.ecommerce.order.product.IProductService;
import com.forsaken.ecommerce.order.product.PurchaseResponse;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.Conversions;
import org.apache.avro.LogicalTypes;
import org.apache.avro.Schema;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements IOrderService {

    private final IOrderRepository orderRepository;
    private final ICustomerService customerService;
    private final IProductService productService;
    private final IPaymentService paymentService;
    private final IOrderProducer orderProducer;

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
        CompletableFuture.allOf(fetchedCustomer, fetchedPurchasedProducts).join();

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

        final OrderConfirmation orderConfirmation = OrderConfirmation.newBuilder()
                .setOrderReference(request.reference())
                .setTotalAmount(convertBigDecimalToBytes(request.amount()))
                .setPaymentMethod(PaymentMethod.valueOf(request.paymentMethod().name()))
                .setCustomer(toAvroCustomer(customer))
                .setProducts(purchasedProducts.stream().map(this::toAvroPurchase).toList())
                .setTraceId("traceId") // TODO tracing will be done later
                .build();
        log.info("Created Order Confirmation: {}", orderConfirmation);
        orderProducer.sendOrderConfirmation(orderConfirmation);
        log.info("Sent Order Confirmation");
        return savedOrder.getId();
    }

    @Override
    public List<OrderResponse> findAllOrders() {
        log.info("Finding All Orders");
        return this.orderRepository.findAll()
                .stream()
                .map(Order::fromOrder)
                .collect(Collectors.toList());
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
    public List<OrderResponse> findAllOrdersByCustomerId(
            final String customerId,
            final LocalDateTime fromDate,
            final LocalDateTime toDate
    ) {
        log.info("Finding All Orders By Customer: {}", customerId);
        return orderRepository.findAllByCustomerIdAndCreatedDateBetween(customerId, fromDate, toDate)
                .stream().map(Order::fromOrder).toList();
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
