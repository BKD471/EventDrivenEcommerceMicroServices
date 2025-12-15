package com.forsaken.ecommerce.notification.mapper;

import com.forsaken.ecommerce.avro.CustomerResponse;
import com.forsaken.ecommerce.avro.PurchaseResponse;
import com.forsaken.ecommerce.notification.models.Product;
import com.forsaken.ecommerce.notification.models.Customer;
import com.forsaken.ecommerce.notification.models.OrderConfirmation;
import com.forsaken.ecommerce.notification.models.PaymentConfirmation;
import com.forsaken.ecommerce.notification.models.PaymentMethod;
import org.apache.avro.Conversions;
import org.apache.avro.LogicalTypes;
import org.apache.avro.Schema;
import org.apache.avro.specific.SpecificRecordBase;

import java.math.BigDecimal;
import java.nio.ByteBuffer;

/**
 * Utility mapper responsible for converting Avro-generated event models
 * into internal domain models used by the Notification service.
 *
 * <p>
 * This class acts as a strict boundary between:
 * </p>
 * <ul>
 *     <li><b>Avro models</b> – used for Kafka serialization/deserialization</li>
 *     <li><b>Domain models</b> – used internally for business logic</li>
 * </ul>
 *
 * <p>
 * Why this separation matters:
 * </p>
 * <ul>
 *     <li>Prevents tight coupling between business logic and wire formats</li>
 *     <li>Allows Avro schema evolution without breaking domain code</li>
 *     <li>Makes refactoring and testing easier</li>
 * </ul>
 *
 * <p>
 * This mapper also handles Avro logical types such as {@code decimal},
 * converting them safely into Java {@link java.math.BigDecimal}.
 * </p>
 *
 * <p><b>Design notes:</b></p>
 * <ul>
 *     <li>All methods are static because the mapper is stateless</li>
 *     <li>Explicit enum mapping is used instead of sharing enums across layers</li>
 *     <li>Null-safety is preserved where applicable</li>
 * </ul>
 */
public class AvroMapper {

    /**
     * Avro schema definition for {@code decimal(18, 2)} logical type.
     *
     * <p>
     * This schema must exactly match the definition used in the corresponding
     * Avro {@code .avsc} file. Any mismatch in precision or scale will result
     * in runtime serialization/deserialization errors.
     * </p>
     */
    private static final Schema DECIMAL_SCHEMA =
            LogicalTypes.decimal(18, 2)
                    .addToSchema(Schema.create(Schema.Type.BYTES));

    /**
     * Avro decimal conversion utility used to convert {@link java.nio.ByteBuffer}
     * values into {@link java.math.BigDecimal}.
     */
    private static final Conversions.DecimalConversion DECIMAL_CONVERSION =
            new Conversions.DecimalConversion();

    /**
     * Maps an Avro {@link com.forsaken.ecommerce.avro.PaymentConfirmation}
     * event into the internal {@link PaymentConfirmation} domain model.
     *
     * @param paymentConfirmation Avro payment confirmation received from Kafka
     * @return mapped {@link PaymentConfirmation} domain object
     */
    public static PaymentConfirmation mapToPaymentConfirmation(
            final com.forsaken.ecommerce.avro.PaymentConfirmation paymentConfirmation
    ) {
        return PaymentConfirmation.builder()
                .orderReference(paymentConfirmation.getOrderReference())
                .paymentMethod(mapPaymentMethod(paymentConfirmation.getPaymentMethod()))
                .amount(fromBytes(paymentConfirmation.getAmount()))
                .customerFirstname(paymentConfirmation.getCustomerFirstname())
                .customerLastname(paymentConfirmation.getCustomerLastname())
                .customerEmail(paymentConfirmation.getCustomerEmail())
                .build();
    }

    /**
     * Maps an Avro {@link com.forsaken.ecommerce.avro.OrderConfirmation}
     * event into the internal {@link OrderConfirmation} domain model.
     *
     * @param orderConfirmation Avro order confirmation received from Kafka
     * @return mapped {@link OrderConfirmation} domain object
     */
    public static OrderConfirmation mapToOrderConfirmation(
            final com.forsaken.ecommerce.avro.OrderConfirmation orderConfirmation
    ) {
        return OrderConfirmation.builder()
                .orderReference(orderConfirmation.getOrderReference())
                .paymentMethod(mapPaymentMethod(orderConfirmation.getPaymentMethod()))
                .totalAmount(fromBytes(orderConfirmation.getTotalAmount()))
                .customer(mapToCustomer(orderConfirmation.getCustomer()))
                .products(orderConfirmation.getProducts()
                        .stream().map(AvroMapper::toProduct)
                        .toList()
                )
                .build();
    }

    /**
     * Maps a customer response object into the internal {@link Customer} domain model.
     *
     * @param customerResponse customer data received from another service
     * @return mapped {@link Customer} domain object
     */
    public static Customer mapToCustomer(final CustomerResponse customerResponse) {
        return Customer.builder()
                .id(customerResponse.getId())
                .firstname(customerResponse.getFirstname())
                .lastname(customerResponse.getLastname())
                .email(customerResponse.getEmail())
                .build();
    }

    /**
     * Maps a purchase response into a {@link Product} domain model.
     *
     * @param purchaseResponse product data received from another service
     * @return mapped {@link Product} domain object
     */
    public static Product toProduct(final PurchaseResponse purchaseResponse) {
        return new Product(
                purchaseResponse.getProductId(),
                purchaseResponse.getName(),
                purchaseResponse.getDescription(),
                fromBytes(purchaseResponse.getPrice()),
                purchaseResponse.getQuantity()
        );
    }

    /**
     * Converts an Avro decimal stored as {@link java.nio.ByteBuffer}
     * into a {@link java.math.BigDecimal}.
     *
     * @param byteBuffer decimal value encoded using Avro logical type
     * @return converted {@link BigDecimal}, or {@code null} if input is null
     */
    public static BigDecimal fromBytes(final ByteBuffer byteBuffer) {
        if (null == byteBuffer) return null;
        return DECIMAL_CONVERSION.fromBytes(byteBuffer, DECIMAL_SCHEMA, DECIMAL_SCHEMA.getLogicalType());
    }


    /**
     * Maps an Avro {@link com.forsaken.ecommerce.avro.PaymentMethod}
     * enum to the internal {@link com.forsaken.ecommerce.notification.models.PaymentMethod}.
     *
     * <p>
     * Explicit enum mapping is used to avoid coupling domain logic
     * to Avro-generated classes.
     * </p>
     *
     * @param avroPaymentMethod payment method from Avro event
     * @return corresponding domain {@link PaymentMethod}, or {@code null}
     */
    public static com.forsaken.ecommerce.notification.models.PaymentMethod mapPaymentMethod(
            final com.forsaken.ecommerce.avro.PaymentMethod avroPaymentMethod
    ) {
        if (null == avroPaymentMethod) return null;
        return com.forsaken.ecommerce.notification.models.PaymentMethod.valueOf(avroPaymentMethod.name());
    }

    /**
     * Extracts the customer’s full name from supported Avro event records.
     *
     * <p>
     * This method provides a unified way to resolve customer identity information
     * from different Avro event types without leaking Avro-specific logic into
     * higher layers of the application.
     * </p>
     *
     * <p>
     * <b>Supported record types:</b>
     * </p>
     * <ul>
     *     <li>
     *         {@link com.forsaken.ecommerce.avro.OrderConfirmation} – resolves the
     *         customer name from the embedded {@code customer} record.
     *     </li>
     *     <li>
     *         {@link com.forsaken.ecommerce.avro.PaymentConfirmation} – resolves the
     *         customer name from flattened customer fields.
     *     </li>
     * </ul>
     *
     * <p>
     * <b>Error handling:</b>
     * </p>
     * <ul>
     *     <li>
     *         Throws {@link IllegalStateException} if an {@code OrderConfirmation}
     *         event contains a {@code null} customer object. This indicates a
     *         contract violation in the upstream service.
     *     </li>
     *     <li>
     *         Throws {@link IllegalArgumentException} for unsupported Avro record
     *         types. This branch is defensive and should never be reached under
     *         normal Kafka deserialization guarantees.
     *     </li>
     * </ul>
     *
     * <p>
     * <b>Design rationale:</b>
     * </p>
     * <ul>
     *     <li>
     *         Uses Java pattern matching for {@code switch} to avoid fragile
     *         schema-name or index-based access.
     *     </li>
     *     <li>
     *         Keeps Avro-specific branching logic centralized in the mapper layer.
     *     </li>
     *     <li>
     *         Prevents duplication of customer name resolution logic across
     *         consumers.
     *     </li>
     * </ul>
     *
     * @param avroRecord the Avro {@link SpecificRecordBase} event containing customer data
     * @return the resolved customer full name in the format {@code "Firstname Lastname"}
     * @throws IllegalStateException    if the customer object is missing in an
     *                                  {@code OrderConfirmation} record
     * @throws IllegalArgumentException if the Avro record type is unsupported
     */
    public static String getCustomerName(final SpecificRecordBase avroRecord) {
        return switch (avroRecord) {
            case com.forsaken.ecommerce.avro.OrderConfirmation order -> {
                final var customer = order.getCustomer();
                if (null == customer) throw new IllegalStateException("Customer is null in OrderConfirmation");
                yield customer.getFirstname() + " " + customer.getLastname();
            }
            case com.forsaken.ecommerce.avro.PaymentConfirmation payment ->
                    payment.getCustomerFirstname() + " " + payment.getCustomerLastname();
            default -> throw new IllegalArgumentException(
                    "Unsupported Avro record type: " + avroRecord.getClass().getName()
            );

        };
    }
}
