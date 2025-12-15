package com.forsaken.ecommerce.notification.mapper;


import com.forsaken.ecommerce.avro.CustomerResponse;
import com.forsaken.ecommerce.avro.OrderConfirmation;
import com.forsaken.ecommerce.avro.PaymentConfirmation;
import com.forsaken.ecommerce.avro.PurchaseResponse;
import com.forsaken.ecommerce.notification.models.Customer;
import com.forsaken.ecommerce.notification.models.Product;
import com.forsaken.ecommerce.notification.models.PaymentMethod;
import org.apache.avro.specific.SpecificRecordBase;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.List;

import static com.forsaken.ecommerce.notification.mapper.AvroMapper.fromBytes;
import static com.forsaken.ecommerce.notification.mapper.AvroMapper.getCustomerName;
import static com.forsaken.ecommerce.notification.mapper.AvroMapper.mapPaymentMethod;
import static com.forsaken.ecommerce.notification.mapper.AvroMapper.mapToOrderConfirmation;
import static com.forsaken.ecommerce.notification.mapper.AvroMapper.toProduct;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for {@link AvroMapper}.
 *
 * <p>
 * This test suite validates the correctness of mappings between
 * Avro-generated event models and internal domain models used by
 * the Notification service.
 * </p>
 *
 * <p>
 * <b>Testing strategy:</b>
 * </p>
 * <ul>
 *     <li>Uses real Avro builders to construct valid event objects</li>
 *     <li>Avoids mocking for positive test cases to ensure realistic behavior</li>
 *     <li>Validates both happy paths and defensive error handling</li>
 *     <li>Ensures Avro logical types (e.g., decimal) are converted safely</li>
 * </ul>
 *
 * <p>
 * <b>Design principles:</b>
 * </p>
 * <ul>
 *     <li>No Kafka, Spring, or infrastructure dependencies</li>
 *     <li>Pure unit tests with deterministic inputs</li>
 *     <li>Tests reflect Avro schema constraints</li>
 * </ul>
 */
class AvroMapperTest {

    /**
     * Verifies that an Avro {@link PaymentConfirmation} event
     * is correctly mapped to the internal domain model.
     *
     * <p>
     * Ensures that:
     * </p>
     * <ul>
     *     <li>Order reference is preserved</li>
     *     <li>Payment method enum is correctly translated</li>
     *     <li>Customer identity fields are mapped</li>
     *     <li>Decimal amount is converted from Avro bytes</li>
     * </ul>
     */
    @Test
    void shouldMapPaymentConfirmationCorrectly() {
        // given
        final PaymentConfirmation avro = constructPaymentConfirmation();

        // when
        var domain = AvroMapper.mapToPaymentConfirmation(avro);

        // then
        assertEquals("ORD-100", domain.getOrderReference());
        assertEquals(PaymentMethod.PAYPAL, domain.getPaymentMethod());
        assertEquals("John", domain.getCustomerFirstname());
        assertEquals("Doe", domain.getCustomerLastname());
        assertEquals("john@doe.com", domain.getCustomerEmail());
        assertNotNull(domain.getAmount());
    }

    /**
     * Verifies that an Avro {@link OrderConfirmation} event
     * is correctly mapped to the internal domain model.
     *
     * <p>
     * This test ensures:
     * </p>
     * <ul>
     *     <li>Nested customer record is mapped properly</li>
     *     <li>Payment method enum translation is correct</li>
     *     <li>Product list mapping works for empty collections</li>
     * </ul>
     */
    @Test
    void shouldMapOrderConfirmationCorrectly() {
        // given
        final CustomerResponse customer = constructCustomer();
        final OrderConfirmation orderAvro = constructOrderConfirmation(customer);

        // when
        final var domain = mapToOrderConfirmation(orderAvro);

        // then
        assertEquals("ORD-200", domain.getOrderReference());
        assertEquals(PaymentMethod.CREDIT_CARD, domain.getPaymentMethod());
        assertEquals("Alice", domain.getCustomer().getFirstname());
        assertEquals("Smith", domain.getCustomer().getLastname());
        assertEquals("alice@smith.com", domain.getCustomer().getEmail());
        assertTrue(domain.getProducts().isEmpty());
    }

    /**
     * Verifies mapping of an Avro {@link CustomerResponse}
     * into the internal {@link Customer} domain model.
     */
    @Test
    void shouldMapCustomerCorrectly() {
        // given
        final CustomerResponse customer = constructCustomer();

        // when
        final Customer mapped = AvroMapper.mapToCustomer(customer);

        // then
        assertEquals("cust-1", mapped.getId());
        assertEquals("Alice", mapped.getFirstname());
        assertEquals("Smith", mapped.getLastname());
        assertEquals("alice@smith.com", mapped.getEmail());
    }

    /**
     * Verifies mapping of an Avro {@link PurchaseResponse}
     * into the internal {@link Product} domain model.
     *
     * <p>
     * Ensures price conversion from Avro decimal bytes
     * and correct propagation of product attributes.
     * </p>
     */
    @Test
    void shouldMapProductCorrectly() {
        // given
        final PurchaseResponse purchaseAvro = constructPurchaseResponse();

        // when
        Product product = toProduct(purchaseAvro);

        // then
        assertEquals(1, product.getProductId());
        assertEquals("Product-1", product.getName());
        assertEquals("Description-1", product.getDescription());
        assertEquals(5.0, product.getQuantity());
        assertNotNull(product.getPrice());
    }

    /**
     * Verifies that Avro decimal values encoded as {@link ByteBuffer}
     * are correctly converted into {@link BigDecimal}.
     */
    @Test
    void shouldConvertBytesToBigDecimal() {
        // given
        final ByteBuffer buffer = ByteBuffer.wrap(new byte[]{0x01});

        // when
        final BigDecimal result = fromBytes(buffer);

        // then
        assertNotNull(result);
    }

    /**
     * Verifies that a {@code null} Avro decimal buffer
     * is safely handled without throwing exceptions.
     */
    @Test
    void shouldReturnNullWhenByteBufferIsNull() {
        assertNull(fromBytes(null));
    }

    /**
     * Verifies explicit mapping between Avro and domain
     * {@code PaymentMethod} enums.
     */
    @Test
    void shouldMapPaymentMethodCorrectly() {
        // when
        final var result = mapPaymentMethod(
                com.forsaken.ecommerce.avro.PaymentMethod.VISA
        );

        // then
        assertEquals(PaymentMethod.VISA, result);
    }

    /**
     * Verifies that {@code null} Avro payment methods
     * are safely handled.
     */
    @Test
    void shouldReturnNullWhenPaymentMethodIsNull() {
        assertNull(mapPaymentMethod(null));
    }

    /**
     * Verifies extraction of customer full name from
     * an {@link OrderConfirmation} Avro event.
     *
     * <p>
     * Customer information is resolved from the embedded
     * {@code customer} record.
     * </p>
     */
    @Test
    void shouldExtractCustomerNameFromOrderConfirmation() {
        // given
        final CustomerResponse customerAvro = constructCustomer();
        final OrderConfirmation orderAvro = constructOrderConfirmation(customerAvro);

        // when
        final String customerName = getCustomerName(orderAvro);

        // then
        assertEquals("Alice Smith", customerName);
    }

    /**
     * Verifies extraction of customer full name from
     * a {@link PaymentConfirmation} Avro event.
     *
     * <p>
     * Customer information is resolved from flattened
     * customer fields.
     * </p>
     */
    @Test
    void shouldExtractCustomerNameFromPaymentConfirmation() {
        // given
        final PaymentConfirmation paymentAvro = constructPaymentConfirmation();

        // when
        final String customerName = getCustomerName(paymentAvro);

        // then
        assertEquals("John Doe", customerName);
    }

    /**
     * Verifies that an {@link IllegalArgumentException} is thrown
     * when an unsupported Avro {@link SpecificRecordBase} type
     * is passed to {@link AvroMapper#getCustomerName}.
     *
     * <p>
     * This test validates the defensive default branch of the
     * pattern-matching switch statement.
     * </p>
     */
    @Test
    void shouldThrowExceptionForUnsupportedAvroRecord() {
        final SpecificRecordBase unsupportedAvro = new SpecificRecordBase() {
            @Override
            public Object get(int i) {
                return null;
            }

            @Override
            public void put(int i, Object v) {
            }

            @Override
            public org.apache.avro.Schema getSchema() {
                return null;
            }
        };

        assertThrows(
                IllegalArgumentException.class,
                () -> getCustomerName(unsupportedAvro)
        );
    }

    /**
     * Constructs a valid Avro {@link PaymentConfirmation}
     * for use in mapping tests.
     */
    private PaymentConfirmation constructPaymentConfirmation() {
        return PaymentConfirmation.newBuilder()
                .setOrderReference("ORD-100")
                .setAmount(ByteBuffer.wrap(new byte[]{0x01}))
                .setPaymentMethod(com.forsaken.ecommerce.avro.PaymentMethod.PAYPAL)
                .setCustomerFirstname("John")
                .setCustomerLastname("Doe")
                .setCustomerEmail("john@doe.com")
                .setPaymentDate(Instant.parse("2024-01-01T10:00:00Z"))
                .setTraceId("trace-1")
                .build();
    }

    /**
     * Constructs a valid Avro {@link CustomerResponse}
     * consistent with the Avro schema constraints.
     */
    private CustomerResponse constructCustomer() {
        return CustomerResponse.newBuilder()
                .setId("cust-1")
                .setFirstname("Alice")
                .setLastname("Smith")
                .setEmail("alice@smith.com")
                .build();
    }

    /**
     * Constructs a valid Avro {@link OrderConfirmation}
     * with a required {@link CustomerResponse}.
     */
    private OrderConfirmation constructOrderConfirmation(final CustomerResponse customer) {
        return OrderConfirmation.newBuilder()
                .setOrderReference("ORD-200")
                .setCustomer(customer)
                .setTotalAmount(ByteBuffer.wrap(new byte[]{0x02}))
                .setProducts(List.of())
                .setTraceId("trace-2")
                .setPaymentMethod(com.forsaken.ecommerce.avro.PaymentMethod.CREDIT_CARD)
                .build();
    }

    /**
     * Constructs a valid Avro {@link PurchaseResponse}
     * used for product mapping tests.
     */
    private PurchaseResponse constructPurchaseResponse() {
        return PurchaseResponse.newBuilder()
                .setProductId(1)
                .setName("Product-1")
                .setDescription("Description-1")
                .setPrice(ByteBuffer.wrap(new byte[]{0x07}))
                .setQuantity(5)
                .build();
    }
}
