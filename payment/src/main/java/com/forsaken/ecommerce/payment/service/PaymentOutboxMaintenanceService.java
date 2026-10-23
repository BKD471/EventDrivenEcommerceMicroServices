package com.forsaken.ecommerce.payment.service;

import com.forsaken.ecommerce.payment.model.PaymentOutboxEvent;
import com.forsaken.ecommerce.payment.repository.IPaymentOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;

/** Housekeeping operations on the payment outbox, driven by the outbox batch jobs. */
@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentOutboxMaintenanceService {

    private final IPaymentOutboxRepository outboxRepository;

    /** Deletes published events from the given ids; rows that are no longer {@code SENT} are kept. */
    @Transactional
    public int purgeSent(final Collection<Long> ids) {
        if (ids.isEmpty()) {
            return 0;
        }
        return outboxRepository.deleteByIdsAndStatus(ids, PaymentOutboxEvent.OutboxStatus.SENT);
    }

    /**
     * Returns a quarantined event to the publish queue when its payload can now be encoded.
     *
     * @return {@code true} if the event was re-queued, {@code false} if it stays quarantined or is gone
     */
    @Transactional
    public boolean requeueQuarantined(final Long id) {
        final PaymentOutboxEvent event = outboxRepository.findById(id).orElse(null);
        if (event == null || event.getStatus() != PaymentOutboxEvent.OutboxStatus.QUARANTINED) {
            return false;
        }
        try {
            PaymentConfirmationMapper.toConfirmation(event);
        } catch (RuntimeException ex) {
            log.warn("Quarantined outbox event for order reference={} is still unpublishable",
                    event.getOrderReference());
            return false;
        }
        event.requeue();
        outboxRepository.save(event);
        log.info("Re-queued quarantined outbox event for order reference={}", event.getOrderReference());
        return true;
    }
}
