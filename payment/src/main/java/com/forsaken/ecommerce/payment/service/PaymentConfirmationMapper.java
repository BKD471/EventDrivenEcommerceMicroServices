package com.forsaken.ecommerce.payment.service;

import com.forsaken.ecommerce.avro.PaymentConfirmation;
import com.forsaken.ecommerce.payment.model.PaymentOutboxEvent;
import org.apache.avro.Conversions;
import org.apache.avro.LogicalTypes;
import org.apache.avro.Schema;

import java.math.BigDecimal;
import java.nio.ByteBuffer;

/** Builds the Kafka payload for an outbox record; throws when the stored data cannot be encoded. */
public final class PaymentConfirmationMapper {

    private PaymentConfirmationMapper() {
    }

    public static PaymentConfirmation toConfirmation(final PaymentOutboxEvent event) {
        return new PaymentConfirmation(
                event.getOrderReference(),
                convertBigDecimalToBytes(event.getAmount()),
                event.getPaymentMethod(),
                event.getCustomerFirstname(),
                event.getCustomerLastname(),
                event.getCustomerEmail(),
                event.getEventTime(),
                event.getTraceId()
        );
    }

    private static ByteBuffer convertBigDecimalToBytes(final BigDecimal value) {
        if (null == value) return null;

        final Schema decimalSchema =
                LogicalTypes.decimal(18, 2).addToSchema(Schema.create(Schema.Type.BYTES));
        return new Conversions.DecimalConversion()
                .toBytes(value.setScale(2, java.math.RoundingMode.UNNECESSARY),
                        decimalSchema, decimalSchema.getLogicalType());
    }
}
