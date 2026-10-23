package com.forsaken.ecommerce.payment.service;

import com.forsaken.ecommerce.avro.PaymentConfirmation;
import com.forsaken.ecommerce.payment.model.PaymentOutboxEvent;
import com.forsaken.ecommerce.payment.repository.IPaymentOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
                confirmation = PaymentConfirmationMapper.toConfirmation(event);
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
}
