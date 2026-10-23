package com.forsaken.ecommerce.payment.repository;

import com.forsaken.ecommerce.payment.model.PaymentOutboxEvent;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface IPaymentOutboxRepository extends JpaRepository<PaymentOutboxEvent, Long> {

    /** Locks due rows and skips rows already claimed by another publisher instance. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("select event from PaymentOutboxEvent event where event.status = :status "
            + "and (event.nextAttemptAt is null or event.nextAttemptAt <= :now) order by event.id asc")
    List<PaymentOutboxEvent> findReadyToPublish(
            @Param("status") PaymentOutboxEvent.OutboxStatus status,
            @Param("now") Instant now,
            Pageable pageable);

    /** Keyset page of event ids with the given status created before the cutoff. */
    @Query("select event.id from PaymentOutboxEvent event where event.status = :status "
            + "and event.eventTime < :cutoff and event.id > :afterId order by event.id asc")
    List<Long> findIdsByStatusOlderThan(
            @Param("status") PaymentOutboxEvent.OutboxStatus status,
            @Param("cutoff") Instant cutoff,
            @Param("afterId") Long afterId,
            Pageable pageable);

    /** Keyset page of event ids with the given status. */
    @Query("select event.id from PaymentOutboxEvent event where event.status = :status "
            + "and event.id > :afterId order by event.id asc")
    List<Long> findIdsByStatus(
            @Param("status") PaymentOutboxEvent.OutboxStatus status,
            @Param("afterId") Long afterId,
            Pageable pageable);

    /** Deletes only rows still in the given status, so an event re-queued meanwhile is never purged. */
    @Modifying
    @Query("delete from PaymentOutboxEvent event where event.id in :ids and event.status = :status")
    int deleteByIdsAndStatus(
            @Param("ids") Collection<Long> ids,
            @Param("status") PaymentOutboxEvent.OutboxStatus status);
}
