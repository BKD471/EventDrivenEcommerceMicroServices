package com.forsaken.ecommerce.product.service;

import com.forsaken.ecommerce.product.dto.ReservationReconciliationItem;
import com.forsaken.ecommerce.product.model.ReservationOrder;
import com.forsaken.ecommerce.product.model.ReservationStatus;
import com.forsaken.ecommerce.product.repository.IReservationOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Read-only reservation queries used by cross-service reconciliation. */
@Service
@RequiredArgsConstructor
public class ReservationReconciliationService {

    private final IReservationOrderRepository reservationOrderRepository;

    @Transactional(readOnly = true)
    public Optional<ReservationReconciliationItem> findByOrderReference(final String orderReference) {
        return reservationOrderRepository.findById(orderReference).map(ReservationReconciliationService::toItem);
    }

    @Transactional(readOnly = true)
    public List<ReservationReconciliationItem> findReservedCreatedBefore(
            final Instant cutoff,
            final String afterReference,
            final int size
    ) {
        return reservationOrderRepository.findReservationsCreatedBefore(
                ReservationStatus.RESERVED, cutoff, afterReference, PageRequest.of(0, size));
    }

    private static ReservationReconciliationItem toItem(final ReservationOrder reservation) {
        return new ReservationReconciliationItem(
                reservation.getOrderReference(),
                reservation.getStatus(),
                reservation.getCreatedAt(),
                reservation.getConfirmedAt(),
                reservation.getReleasedAt());
    }
}
