package com.forsaken.ecommerce.payment.repository;

import com.forsaken.ecommerce.payment.model.PaymentOutboxEvent;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.QueryHints;

import java.util.List;

public interface IPaymentOutboxRepository extends JpaRepository<PaymentOutboxEvent, Long> {

    /** Locks the rows and skips rows already claimed by another publisher instance. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    List<PaymentOutboxEvent> findTop50ByStatusOrderByIdAsc(PaymentOutboxEvent.OutboxStatus status);
}
