package com.forsaken.ecommerce.order.order.service;

import com.forsaken.ecommerce.order.order.model.OrderSagaStatus;
import com.forsaken.ecommerce.order.order.repository.IOrderRepository;
import com.forsaken.ecommerce.order.payment.IPaymentService;
import com.forsaken.ecommerce.order.payment.PaymentReconciliationItem;
import com.forsaken.ecommerce.order.product.ProductReconciliationClient;
import com.forsaken.ecommerce.order.product.ProductReservationReconciliationItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Read-only consistency checks across orders, payments, and inventory. A remote lookup failure propagates so
 * the batch step retries or skips the item; it never produces a finding.
 */
@Service
@RequiredArgsConstructor
public class OrderReconciliationService {

    private static final String PAYMENT_COMPLETED = "COMPLETED";
    private static final String RESERVATION_RESERVED = "RESERVED";

    private final IOrderRepository orderRepository;
    private final IPaymentService paymentService;
    private final ProductReconciliationClient productReconciliationClient;

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

    /** A completed order must have an inventory reservation confirmed by the product service. */
    public Optional<ReconciliationFinding> checkCompletedOrderReservation(final Integer orderId) {
        if (orderRepository.findSagaStatusById(orderId).orElse(null) != OrderSagaStatus.COMPLETED) {
            return Optional.empty();
        }
        final String reference = orderRepository.findReferenceById(orderId).orElse(null);
        if (reference == null) {
            return Optional.empty();
        }
        final Optional<ProductReservationReconciliationItem> reservation =
                productReconciliationClient.findByOrderReference(reference);
        if (reservation.isEmpty()
                || !RESERVATION_RESERVED.equals(reservation.get().status())
                || reservation.get().confirmedAt() == null) {
            return Optional.of(new ReconciliationFinding(
                    ReconciliationFinding.Type.COMPLETED_ORDER_WITHOUT_CONFIRMED_RESERVATION,
                    orderId, null, "Completed order reference=" + reference
                    + " has no confirmed inventory reservation"));
        }
        return Optional.empty();
    }

    /** A failed order must not retain an active inventory reservation. */
    public Optional<ReconciliationFinding> checkFailedOrderReservation(final Integer orderId) {
        if (orderRepository.findSagaStatusById(orderId).orElse(null) != OrderSagaStatus.FAILED) {
            return Optional.empty();
        }
        final String reference = orderRepository.findReferenceById(orderId).orElse(null);
        if (reference == null) {
            return Optional.empty();
        }
        final Optional<ProductReservationReconciliationItem> reservation =
                productReconciliationClient.findByOrderReference(reference);
        if (reservation.isPresent() && RESERVATION_RESERVED.equals(reservation.get().status())) {
            return Optional.of(new ReconciliationFinding(
                    ReconciliationFinding.Type.FAILED_ORDER_WITH_UNRELEASED_RESERVATION,
                    orderId, null, "Failed order reference=" + reference
                    + " still has an active inventory reservation"));
        }
        return Optional.empty();
    }

    /** An old active reservation must refer to an order still present in the order service. */
    public Optional<ReconciliationFinding> checkReservationOrder(
            final ProductReservationReconciliationItem reservation
    ) {
        if (orderRepository.findSagaStatusByReference(reservation.orderReference()).isEmpty()) {
            return Optional.of(new ReconciliationFinding(
                    ReconciliationFinding.Type.RESERVATION_WITHOUT_ORDER,
                    null, null, "Active inventory reservation has no matching order reference="
                    + reservation.orderReference()));
        }
        return Optional.empty();
    }
}
