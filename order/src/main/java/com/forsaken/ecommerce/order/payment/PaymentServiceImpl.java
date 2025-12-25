package com.forsaken.ecommerce.order.payment;


import com.forsaken.ecommerce.common.exceptions.PaymentFailedExceptions;
import com.forsaken.ecommerce.common.responses.ApiResponse;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImpl implements IPaymentService {

    private final IPaymentClient paymentClient;
    private final Class<?> className = PaymentServiceImpl.class;

    @Override
    @Async("appTaskExecutor")
    public CompletableFuture<Integer> pay(final PaymentRequest request) throws PaymentFailedExceptions {
        log.info("Payment request: {}", request);
        try {
            final ApiResponse<Integer> paymentResponse = paymentClient.requestOrderPayment(request);
            if (null == paymentResponse || null == paymentResponse.data()) {
                throw new PaymentFailedExceptions(
                        "Payment Failed:: An error occurred while processing the payment",
                        "pay(final PaymentRequest request) in " + className
                );
            }
            return CompletableFuture.completedFuture(paymentResponse.data());
        } catch (FeignException.NotFound ex) {
            throw new PaymentFailedExceptions(
                    "Payment Failed: Payment service endpoint not found",
                    "pay(final PaymentRequest request) in " + className,
                    ex
            );

        } catch (FeignException.BadRequest ex) {
            throw new PaymentFailedExceptions(
                    "Payment Failed: Invalid payment request",
                    "pay(final PaymentRequest request) in " + className,
                    ex
            );

        } catch (FeignException ex) {
            throw new PaymentFailedExceptions(
                    "Payment Failed: Payment service error (status=" + ex.status() + ")",
                    "pay(final PaymentRequest request) in " + className,
                    ex
            );
        }
    }
}
