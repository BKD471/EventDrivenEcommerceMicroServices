package com.forsaken.ecommerce.product.service;

import com.forsaken.ecommerce.product.model.ReservationOrder;
import com.forsaken.ecommerce.product.model.ReservationStatus;
import com.forsaken.ecommerce.product.order.IOrderStatusClient;
import com.forsaken.ecommerce.product.repository.IProductReservationRepository;
import com.forsaken.ecommerce.product.repository.IReservationOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Housekeeping for inventory reservations, driven by the reservation cleanup batch jobs.
 * Each operation is safe to repeat and runs in its own transaction.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ReservationCleanupService {

    static final String FAILED = "FAILED";
    static final String COMPLETED = "COMPLETED";

    private final IReservationOrderRepository reservationOrderRepository;
    private final IProductReservationRepository reservationRepository;
    private final IProductService productService;
    private final IOrderStatusClient orderStatusClient;

    /**
     * Releases a stale reservation (restoring stock) only when the order service reports the order as FAILED or
     * does not know it. Orders that are in flight or awaiting compensation keep their reservation. COMPLETED orders keep it
     * permanently (RESERVED is also the final state of a completed order) and are marked confirmed so they are
     * not examined again.
     *
     * @return {@code true} if the reservation was released, {@code false} if it was left untouched
     * @throws com.forsaken.ecommerce.product.order.OrderStatusLookupException if the order state is unknown
     */
    public boolean expireIfOrderIsDead(final String orderReference) {
        final Optional<ReservationOrder> reservation = reservationOrderRepository.findById(orderReference);
        if (reservation.isEmpty() || reservation.get().getStatus() != ReservationStatus.RESERVED) {
            return false;
        }
        final Optional<String> sagaStatus = orderStatusClient.findSagaStatus(orderReference);
        if (sagaStatus.isPresent() && COMPLETED.equals(sagaStatus.get())) {
            reservationOrderRepository.markConfirmed(orderReference, Instant.now());
            return false;
        }
        if (sagaStatus.isPresent() && !FAILED.equals(sagaStatus.get())) {
            return false;
        }
        productService.releaseReservation(orderReference);
        log.info("Expired stale reservation for order reference={} (order status={})",
                orderReference, sagaStatus.orElse("MISSING"));
        return true;
    }

    /**
     * Deletes a RELEASED tombstone and its reservation rows once it is older than the cutoff. The row is locked and
     * re-checked first, so a record that was changed meanwhile is never deleted.
     *
     * @return {@code true} if the tombstone was deleted
     */
    @Transactional
    public boolean purgeTombstone(final String orderReference, final Instant releasedBefore) {
        final Optional<ReservationOrder> tombstone =
                reservationOrderRepository.findByOrderReferenceForUpdate(orderReference);
        if (tombstone.isEmpty()
                || tombstone.get().getStatus() != ReservationStatus.RELEASED
                || tombstone.get().getReleasedAt() == null
                || !tombstone.get().getReleasedAt().isBefore(releasedBefore)) {
            return false;
        }
        reservationRepository.deleteAllByOrderReference(orderReference);
        reservationOrderRepository.delete(tombstone.get());
        return true;
    }
}
