package com.forsaken.ecommerce.order.order.service;

import com.forsaken.ecommerce.order.order.model.OrderSagaStatus;
import com.forsaken.ecommerce.order.order.repository.IOrderRepository;
import com.forsaken.ecommerce.order.payment.IPaymentService;
import com.forsaken.ecommerce.order.payment.PaymentLookupException;
import com.forsaken.ecommerce.order.payment.PaymentReconciliationItem;
import com.forsaken.ecommerce.order.product.ProductReconciliationClient;
import com.forsaken.ecommerce.order.product.ProductReservationReconciliationItem;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.time.Instant;

import static com.forsaken.ecommerce.order.order.service.ReconciliationFinding.Type.AUTHORIZED_PAYMENT_FOR_FAILED_ORDER;
import static com.forsaken.ecommerce.order.order.service.ReconciliationFinding.Type.AUTHORIZED_PAYMENT_WITHOUT_ORDER;
import static com.forsaken.ecommerce.order.order.service.ReconciliationFinding.Type.COMPLETED_ORDER_PAYMENT_NOT_COMPLETED;
import static com.forsaken.ecommerce.order.order.service.ReconciliationFinding.Type.COMPLETED_ORDER_WITHOUT_PAYMENT;
import static com.forsaken.ecommerce.order.order.service.ReconciliationFinding.Type.COMPLETED_ORDER_WITHOUT_CONFIRMED_RESERVATION;
import static com.forsaken.ecommerce.order.order.service.ReconciliationFinding.Type.FAILED_ORDER_WITH_UNRELEASED_RESERVATION;
import static com.forsaken.ecommerce.order.order.service.ReconciliationFinding.Type.RESERVATION_WITHOUT_ORDER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderReconciliationServiceTest {

    private final IOrderRepository orders = mock(IOrderRepository.class);
    private final IPaymentService payments = mock(IPaymentService.class);
    private final ProductReconciliationClient productReservations = mock(ProductReconciliationClient.class);
    private final OrderReconciliationService service =
            new OrderReconciliationService(orders, payments, productReservations);

    private void orderIs(final OrderSagaStatus status) {
        when(orders.findSagaStatusById(1)).thenReturn(Optional.ofNullable(status));
    }

    @Test
    void completedOrderWithCompletedPaymentIsConsistent() {
        orderIs(OrderSagaStatus.COMPLETED);
        when(payments.findStatus(1)).thenReturn(Optional.of("COMPLETED"));

        assertTrue(service.checkCompletedOrder(1).isEmpty());
    }

    @Test
    void completedOrderWithoutPaymentIsFlagged() {
        orderIs(OrderSagaStatus.COMPLETED);
        when(payments.findStatus(1)).thenReturn(Optional.empty());

        assertEquals(COMPLETED_ORDER_WITHOUT_PAYMENT, service.checkCompletedOrder(1).orElseThrow().type());
    }

    @Test
    void completedOrderWithAuthorizedPaymentIsFlagged() {
        orderIs(OrderSagaStatus.COMPLETED);
        when(payments.findStatus(1)).thenReturn(Optional.of("AUTHORIZED"));

        assertEquals(COMPLETED_ORDER_PAYMENT_NOT_COMPLETED, service.checkCompletedOrder(1).orElseThrow().type());
    }

    @Test
    void orderThatIsNoLongerCompletedIsNotChecked() {
        orderIs(OrderSagaStatus.COMPENSATION_REQUIRED);

        assertTrue(service.checkCompletedOrder(1).isEmpty());
        verifyNoInteractions(payments);
    }

    @Test
    void paymentLookupFailurePropagatesInsteadOfFlagging() {
        orderIs(OrderSagaStatus.COMPLETED);
        when(payments.findStatus(1)).thenThrow(new PaymentLookupException("down", null));

        assertThrows(PaymentLookupException.class, () -> service.checkCompletedOrder(1));
    }

    @Test
    void authorizedPaymentWithoutOrderIsFlagged() {
        orderIs(null);

        assertEquals(AUTHORIZED_PAYMENT_WITHOUT_ORDER,
                service.checkAuthorizedPayment(new PaymentReconciliationItem(9, 1, "AUTHORIZED"))
                        .orElseThrow().type());
    }

    @Test
    void authorizedPaymentForFailedOrderIsFlagged() {
        orderIs(OrderSagaStatus.FAILED);

        assertEquals(AUTHORIZED_PAYMENT_FOR_FAILED_ORDER,
                service.checkAuthorizedPayment(new PaymentReconciliationItem(9, 1, "AUTHORIZED"))
                        .orElseThrow().type());
    }

    @Test
    void authorizedPaymentForInFlightOrderIsConsistent() {
        orderIs(OrderSagaStatus.PENDING);

        assertTrue(service.checkAuthorizedPayment(new PaymentReconciliationItem(9, 1, "AUTHORIZED")).isEmpty());
    }

    @Test
    void completedOrderWithoutConfirmedReservationIsFlagged() {
        orderIs(OrderSagaStatus.COMPLETED);
        when(orders.findReferenceById(1)).thenReturn(Optional.of("order-1"));
        when(productReservations.findByOrderReference("order-1")).thenReturn(Optional.of(
                new ProductReservationReconciliationItem("order-1", "RESERVED", Instant.now(), null, null)));

        assertEquals(COMPLETED_ORDER_WITHOUT_CONFIRMED_RESERVATION,
                service.checkCompletedOrderReservation(1).orElseThrow().type());
    }

    @Test
    void completedOrderWithConfirmedReservationIsConsistent() {
        orderIs(OrderSagaStatus.COMPLETED);
        when(orders.findReferenceById(1)).thenReturn(Optional.of("order-1"));
        when(productReservations.findByOrderReference("order-1")).thenReturn(Optional.of(
                new ProductReservationReconciliationItem(
                        "order-1", "RESERVED", Instant.now(), Instant.now(), null)));

        assertTrue(service.checkCompletedOrderReservation(1).isEmpty());
    }

    @Test
    void failedOrderWithActiveReservationIsFlagged() {
        orderIs(OrderSagaStatus.FAILED);
        when(orders.findReferenceById(1)).thenReturn(Optional.of("order-1"));
        when(productReservations.findByOrderReference("order-1")).thenReturn(Optional.of(
                new ProductReservationReconciliationItem("order-1", "RESERVED", Instant.now(), null, null)));

        assertEquals(FAILED_ORDER_WITH_UNRELEASED_RESERVATION,
                service.checkFailedOrderReservation(1).orElseThrow().type());
    }

    @Test
    void oldReservationWithoutOrderIsFlagged() {
        when(orders.findSagaStatusByReference("missing-order")).thenReturn(Optional.empty());

        assertEquals(RESERVATION_WITHOUT_ORDER,
                service.checkReservationOrder(new ProductReservationReconciliationItem(
                        "missing-order", "RESERVED", Instant.now(), null, null)).orElseThrow().type());
    }

    @Test
    void orderStatusChangePreventsReservationLookup() {
        orderIs(OrderSagaStatus.COMPENSATION_REQUIRED);

        assertTrue(service.checkFailedOrderReservation(1).isEmpty());
        verifyNoInteractions(productReservations);
    }

    @Test
    void completedOrderWithoutReservationRecordIsFlagged() {
        orderIs(OrderSagaStatus.COMPLETED);
        when(orders.findReferenceById(1)).thenReturn(Optional.of("order-1"));
        when(productReservations.findByOrderReference("order-1")).thenReturn(Optional.empty());

        assertEquals(COMPLETED_ORDER_WITHOUT_CONFIRMED_RESERVATION,
                service.checkCompletedOrderReservation(1).orElseThrow().type());
        verify(productReservations).findByOrderReference("order-1");
    }
}
