package com.forsaken.ecommerce.order.payment;


import com.forsaken.ecommerce.common.exceptions.PaymentFailedExceptions;
import com.forsaken.ecommerce.common.responses.ApiResponse;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import static feign.FeignException.NotFound;
import static feign.FeignException.BadRequest;

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
            final ApiResponse<Integer> paymentResponse = paymentClient.requestOrderPayment(request);
            if (null == paymentResponse || null == paymentResponse.data()) {
                throw new PaymentFailedExceptions(
                        "Payment Failed: An error occurred while processing the payment",
                        "pay(final PaymentRequest request) in " + CLASS
                );
            }
            return paymentResponse.data();
        } catch (NotFound ex) {
            log.warn(
                    "Payment service endpoint not found. method=pay, status={}, responseBody={}",
                    ex.status(),
                    ex.contentUTF8(),
                    ex
            );
            throw new PaymentFailedExceptions(
                    "Payment Failed: Payment service endpoint not found",
                    "pay(final PaymentRequest request) in " + CLASS,
                    ex
            );

        } catch (BadRequest ex) {
            log.error(
                    "Invalid payment request sent to payment service. method=pay, status={}, responseBody={}",
                    ex.status(),
                    ex.contentUTF8(),
                    ex
            );
            throw new PaymentFailedExceptions(
                    "Payment Failed: Invalid payment request",
                    "pay(final PaymentRequest request) in " + CLASS,
                    ex
            );

        } catch (FeignException ex) {
            log.error(
                    "Payment service call failed. method=pay, status={}, responseBody={}",
                    ex.status(),
                    ex.contentUTF8(),
                    ex
            );
            throw new PaymentFailedExceptions(
                    "Payment Failed: Payment service error (status=" + ex.status() + ")",
                    "pay(final PaymentRequest request) in " + CLASS,
                    ex
            );
        } catch (Exception ex) {
            log.error("Unexpected error while processing payment. method=pay", ex);
            throw new PaymentFailedExceptions(
                    "Unexpected error while processing payment",
                    "pay(PaymentRequest) in " + CLASS,
                    ex
            );
        }
    }
}
