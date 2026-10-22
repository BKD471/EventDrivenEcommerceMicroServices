package com.forsaken.ecommerce.order.payment;

import com.forsaken.ecommerce.common.exceptions.PaymentFailedExceptions;
import com.forsaken.ecommerce.common.responses.ApiResponse;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import static feign.FeignException.BadRequest;
import static feign.FeignException.NotFound;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImpl implements IPaymentService {

    private static final Class<?> CLASS = PaymentServiceImpl.class;
    private final IPaymentClient paymentClient;

    @Override
    public Integer pay(final PaymentRequest request) throws PaymentFailedExceptions {
        log.info("Initiating payment for orderId={}, amount={}", request.orderId(), request.amount());
        try {
            final ApiResponse<Integer> response = paymentClient.requestOrderPayment(request);
            if (response == null || response.data() == null) {
                throw new PaymentFailedExceptions(
                        "Payment Failed: An error occurred while processing the payment",
                        "pay(final PaymentRequest request) in " + CLASS
                );
            }
            return response.data();
        } catch (NotFound ex) {
            log.warn("Payment service endpoint not found. status={}", ex.status(), ex);
            throw new PaymentFailedExceptions(
                    "Payment Failed: Payment service endpoint not found",
                    "pay(final PaymentRequest request) in " + CLASS,
                    ex
            );
        } catch (BadRequest ex) {
            log.error("Invalid payment request sent to payment service. status={}", ex.status(), ex);
            throw new PaymentFailedExceptions(
                    "Payment Failed: Invalid payment request",
                    "pay(final PaymentRequest request) in " + CLASS,
                    ex
            );
        } catch (FeignException ex) {
            log.error("Payment request failed. status={}, responseBody={}",
                    ex.status(), ex.contentUTF8(), ex);
            throw new PaymentFailedExceptions(
                    "Payment Failed: Payment service error (status=" + ex.status() + ")",
                    "pay(final PaymentRequest request) in " + CLASS,
                    ex
            );
        } catch (Exception ex) {
            log.error("Unexpected error while processing payment", ex);
            throw new PaymentFailedExceptions(
                    "Unexpected error while processing payment",
                    "pay(PaymentRequest) in " + CLASS,
                    ex
            );
        }
    }

    @Override
    public void confirm(final Integer orderId) throws PaymentFailedExceptions {
        invokePaymentAction(orderId, true);
    }

    @Override
    public void cancel(final Integer orderId) throws PaymentFailedExceptions {
        invokePaymentAction(orderId, false);
    }

    private void invokePaymentAction(final Integer orderId, final boolean confirm)
            throws PaymentFailedExceptions {
        try {
            final ApiResponse<Void> response = confirm
                    ? paymentClient.confirmOrderPayment(orderId)
                    : paymentClient.cancelOrderPayment(orderId);
            if (response == null || response.status() != ApiResponse.Status.SUCCESS) {
                throw new PaymentFailedExceptions(
                        "Payment service rejected " + (confirm ? "confirmation" : "cancellation"),
                        "invokePaymentAction(Integer, boolean) in " + CLASS
                );
            }
        } catch (PaymentFailedExceptions ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Payment {} failed for orderId={}",
                    confirm ? "confirmation" : "cancellation", orderId, ex);
            throw new PaymentFailedExceptions(
                    "Payment service " + (confirm ? "confirmation" : "cancellation") + " failed",
                    "invokePaymentAction(Integer, boolean) in " + CLASS,
                    ex
            );
        }
    }
}
