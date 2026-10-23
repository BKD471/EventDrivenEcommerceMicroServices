package com.forsaken.ecommerce.product.service;

import com.forsaken.ecommerce.product.model.ReservationOrder;
import com.forsaken.ecommerce.product.model.ReservationStatus;
import com.forsaken.ecommerce.product.order.IOrderStatusClient;
import com.forsaken.ecommerce.product.order.OrderStatusLookupException;
import com.forsaken.ecommerce.product.repository.IProductReservationRepository;
import com.forsaken.ecommerce.product.repository.IReservationOrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservationCleanupServiceTest {

    @Mock
    private IReservationOrderRepository reservationOrderRepository;
    @Mock
    private IProductReservationRepository reservationRepository;
    @Mock
    private IProductService productService;
    @Mock
    private IOrderStatusClient orderStatusClient;

    @InjectMocks
    private ReservationCleanupService service;

    private void reserved(final String reference) {
        when(reservationOrderRepository.findById(reference))
                .thenReturn(Optional.of(new ReservationOrder(reference, ReservationStatus.RESERVED)));
    }

    @Test
    void shouldReleaseWhenOrderFailed() {
        reserved("A");
        when(orderStatusClient.findSagaStatus("A")).thenReturn(Optional.of("FAILED"));

        assertThat(service.expireIfOrderIsDead("A")).isTrue();

        verify(productService).releaseReservation("A");
    }

    @Test
    void shouldReleaseWhenOrderIsUnknown() {
        reserved("B");
        when(orderStatusClient.findSagaStatus("B")).thenReturn(Optional.empty());

        assertThat(service.expireIfOrderIsDead("B")).isTrue();

        verify(productService).releaseReservation("B");
    }

    @Test
    void shouldKeepReservationOfLiveOrders() {
        reserved("C");
        when(orderStatusClient.findSagaStatus("C")).thenReturn(Optional.of("CONFIRMING"));

        assertThat(service.expireIfOrderIsDead("C")).isFalse();

        verify(productService, never()).releaseReservation(any());
        verify(reservationOrderRepository, never()).markConfirmed(any(), any());
    }

    @Test
    void shouldKeepAndConfirmReservationOfCompletedOrders() {
        reserved("D");
        when(orderStatusClient.findSagaStatus("D")).thenReturn(Optional.of("COMPLETED"));

        assertThat(service.expireIfOrderIsDead("D")).isFalse();

        verify(reservationOrderRepository).markConfirmed(any(), any());
        verify(productService, never()).releaseReservation(any());
    }

    @Test
    void shouldNotReleaseWhenOrderLookupFails() {
        reserved("E");
        when(orderStatusClient.findSagaStatus("E")).thenThrow(new OrderStatusLookupException("down"));

        assertThatThrownBy(() -> service.expireIfOrderIsDead("E"))
                .isInstanceOf(OrderStatusLookupException.class);

        verify(productService, never()).releaseReservation(any());
    }

    @Test
    void shouldIgnoreAlreadyReleasedOrMissingReservations() {
        when(reservationOrderRepository.findById("F"))
                .thenReturn(Optional.of(new ReservationOrder("F", ReservationStatus.RELEASED)));
        when(reservationOrderRepository.findById("G")).thenReturn(Optional.empty());

        assertThat(service.expireIfOrderIsDead("F")).isFalse();
        assertThat(service.expireIfOrderIsDead("G")).isFalse();

        verify(orderStatusClient, never()).findSagaStatus(any());
    }

    @Test
    void shouldPurgeOldTombstoneAndItsReservations() {
        final ReservationOrder tombstone = new ReservationOrder("H", ReservationStatus.RELEASED);
        when(reservationOrderRepository.findByOrderReferenceForUpdate("H")).thenReturn(Optional.of(tombstone));

        assertThat(service.purgeTombstone("H", Instant.now().plusSeconds(60))).isTrue();

        verify(reservationRepository).deleteAllByOrderReference("H");
        verify(reservationOrderRepository).delete(tombstone);
    }

    @Test
    void shouldKeepRecentOrNonReleasedTombstones() {
        when(reservationOrderRepository.findByOrderReferenceForUpdate("I"))
                .thenReturn(Optional.of(new ReservationOrder("I", ReservationStatus.RELEASED)));
        when(reservationOrderRepository.findByOrderReferenceForUpdate("J"))
                .thenReturn(Optional.of(new ReservationOrder("J", ReservationStatus.RESERVED)));

        assertThat(service.purgeTombstone("I", Instant.now().minusSeconds(3600))).isFalse();
        assertThat(service.purgeTombstone("J", Instant.now().plusSeconds(60))).isFalse();

        verify(reservationRepository, never()).deleteAllByOrderReference(any());
    }
}
