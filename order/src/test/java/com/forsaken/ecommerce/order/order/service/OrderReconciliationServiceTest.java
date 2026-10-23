package com.forsaken.ecommerce.order.order.service;

import com.forsaken.ecommerce.order.order.model.OrderSagaStatus;
import com.forsaken.ecommerce.order.order.repository.IOrderRepository;
import com.forsaken.ecommerce.order.payment.IPaymentService;
import com.forsaken.ecommerce.order.payment.PaymentLookupException;
import com.forsaken.ecommerce.order.payment.PaymentReconciliationItem;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static com.forsaken.ecommerce.order.order.service.ReconciliationFinding.Type.AUTHORIZED_PAYMENT_FOR_FAILED_ORDER;
import static com.forsaken.ecommerce.order.order.service.ReconciliationFinding.Type.AUTHORIZED_PAYMENT_WITHOUT_ORDER;
import static com.forsaken.ecommerce.order.order.service.ReconciliationFinding.Type.COMPLETED_ORDER_PAYMENT_NOT_COMPLETED;
import static com.forsaken.ecommerce.order.order.service.ReconciliationFinding.Type.COMPLETED_ORDER_WITHOUT_PAYMENT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OrderReconciliationServiceTest {

    private final IOrderRepository orders = mock(IOrderRepository.class);
    private final IPaymentService payments = mock(IPaymentService.class);
    private final OrderReconciliationService service = new OrderReconciliationService(orders, payments);

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
}
