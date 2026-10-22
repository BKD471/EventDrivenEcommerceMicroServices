package com.forsaken.ecommerce.payment.service;

import com.forsaken.ecommerce.avro.PaymentConfirmation;
import com.forsaken.ecommerce.payment.model.PaymentOutboxEvent;
import com.forsaken.ecommerce.payment.repository.IPaymentOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.Conversions;
import org.apache.avro.LogicalTypes;
import org.apache.avro.Schema;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.ByteBuffer;

/**
 * Publishes committed payment confirmation outbox records to Kafka, retrying until acknowledged.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PaymentOutboxPublisher {

    private static final int MAX_ATTEMPTS = 10;

    private final IPaymentOutboxRepository outboxRepository;
    private final INotificationProducerService notificationProducer;

    @Scheduled(fixedDelayString = "${payment.outbox.publish-delay:2000}")
    @Transactional
    public void publishPending() {
        for (final PaymentOutboxEvent event
                : outboxRepository.findTop50ByStatusOrderByIdAsc(PaymentOutboxEvent.OutboxStatus.PENDING)) {
            try {
                notificationProducer.sendNotification(toConfirmation(event));
                event.markSent();
                outboxRepository.save(event);
            } catch (Exception ex) {
                event.recordFailure(MAX_ATTEMPTS);
                outboxRepository.save(event);
                log.error("Unable to publish payment confirmation for order reference={} (attempt {}/{}, status {})",
                        event.getOrderReference(), event.getAttempts(), MAX_ATTEMPTS, event.getStatus(), ex);
            }
        }
    }

    private PaymentConfirmation toConfirmation(final PaymentOutboxEvent event) {
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

    private ByteBuffer convertBigDecimalToBytes(final BigDecimal value) {
        if (null == value) return null;

        final Schema decimalSchema =
                LogicalTypes.decimal(18, 2).addToSchema(Schema.create(Schema.Type.BYTES));
        return new Conversions.DecimalConversion()
                .toBytes(value.setScale(2, java.math.RoundingMode.UNNECESSARY),
                        decimalSchema, decimalSchema.getLogicalType());
    }
}
