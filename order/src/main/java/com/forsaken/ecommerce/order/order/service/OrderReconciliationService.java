package com.forsaken.ecommerce.order.order.service;

import com.forsaken.ecommerce.order.order.model.OrderSagaStatus;
import com.forsaken.ecommerce.order.order.repository.IOrderRepository;
import com.forsaken.ecommerce.order.payment.IPaymentService;
import com.forsaken.ecommerce.order.payment.PaymentReconciliationItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Read-only consistency checks between orders and payments. A lookup failure propagates as an exception so
 * the batch step retries or skips the item; it never produces a finding.
 */
@Service
@RequiredArgsConstructor
public class OrderReconciliationService {

    private static final String PAYMENT_COMPLETED = "COMPLETED";

    private final IOrderRepository orderRepository;
    private final IPaymentService paymentService;

    /** A COMPLETED order must have a COMPLETED payment. */
    public Optional<ReconciliationFinding> checkCompletedOrder(final Integer orderId) {
        final OrderSagaStatus status = orderRepository.findSagaStatusById(orderId).orElse(null);
        if (status != OrderSagaStatus.COMPLETED) {
            return Optional.empty();
        }
        final Optional<String> payment = paymentService.findStatus(orderId);
        if (payment.isEmpty()) {
            return Optional.of(new ReconciliationFinding(
                    ReconciliationFinding.Type.COMPLETED_ORDER_WITHOUT_PAYMENT, orderId, null,
                    "Order is COMPLETED but no payment exists"));
        }
        if (!PAYMENT_COMPLETED.equals(payment.get())) {
            return Optional.of(new ReconciliationFinding(
                    ReconciliationFinding.Type.COMPLETED_ORDER_PAYMENT_NOT_COMPLETED, orderId, null,
                    "Order is COMPLETED but payment is " + payment.get()));
        }
        return Optional.empty();
    }

    /**
     * An AUTHORIZED payment must belong to an order that is still in flight or completing. A missing or
     * FAILED order means the authorization hold was leaked.
     */
    public Optional<ReconciliationFinding> checkAuthorizedPayment(final PaymentReconciliationItem payment) {
        final OrderSagaStatus status = orderRepository.findSagaStatusById(payment.orderId()).orElse(null);
        if (status == null) {
            return Optional.of(new ReconciliationFinding(
                    ReconciliationFinding.Type.AUTHORIZED_PAYMENT_WITHOUT_ORDER, payment.orderId(), payment.id(),
                    "Payment is AUTHORIZED but no matching order exists"));
        }
        if (status == OrderSagaStatus.FAILED) {
            return Optional.of(new ReconciliationFinding(
                    ReconciliationFinding.Type.AUTHORIZED_PAYMENT_FOR_FAILED_ORDER, payment.orderId(), payment.id(),
                    "Payment is AUTHORIZED but the order FAILED"));
        }
        return Optional.empty();
    }
}
