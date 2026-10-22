package com.forsaken.ecommerce.payment.service;

import com.forsaken.ecommerce.avro.PaymentConfirmation;
import com.forsaken.ecommerce.payment.model.PaymentOutboxEvent;
import com.forsaken.ecommerce.payment.repository.IPaymentOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.Conversions;
import org.apache.avro.LogicalTypes;
import org.apache.avro.Schema;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.time.Instant;

/**
 * Publishes committed payment confirmation outbox records to Kafka, retrying until acknowledged.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PaymentOutboxPublisher {

    private final IPaymentOutboxRepository outboxRepository;
    private final INotificationProducerService notificationProducer;

    @Scheduled(fixedDelayString = "${payment.outbox.publish-delay:2000}")
    @Transactional
    public void publishPending() {
        final Instant now = Instant.now();
        for (final PaymentOutboxEvent event
                : outboxRepository.findReadyToPublish(
                        PaymentOutboxEvent.OutboxStatus.PENDING, now, PageRequest.of(0, 50))) {
            final PaymentConfirmation confirmation;
            try {
                confirmation = toConfirmation(event);
            } catch (RuntimeException ex) {
                event.quarantine();
                outboxRepository.save(event);
                log.error("Unable to build payment confirmation for order reference={}, quarantining event",
                        event.getOrderReference(), ex);
                continue;
            }

            try {
                notificationProducer.sendNotification(confirmation);
                event.markSent();
                outboxRepository.save(event);
            } catch (Exception ex) {
                event.recordFailure(Instant.now());
                outboxRepository.save(event);
                log.error("Unable to publish payment confirmation for order reference={} (attempt {}, retry at {})",
                        event.getOrderReference(), event.getAttempts(), event.getNextAttemptAt(), ex);
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
