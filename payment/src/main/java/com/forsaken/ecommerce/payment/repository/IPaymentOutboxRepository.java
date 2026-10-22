package com.forsaken.ecommerce.payment.repository;

import com.forsaken.ecommerce.payment.model.PaymentOutboxEvent;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
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
}
