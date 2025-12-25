package com.forsaken.ecommerce.order.payment;


import com.forsaken.ecommerce.common.exceptions.PaymentFailedExceptions;
import com.forsaken.ecommerce.common.responses.ApiResponse;
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
            final ApiResponse<Integer> response = paymentClient.requestOrderPayment(request);
            return CompletableFuture.completedFuture(response.data());
        } catch (feign.FeignException.NotFound ex) {
            throw new PaymentFailedExceptions(
                    "Payment Failed:: An error occurred while processing the payment",
                    "pay(final PaymentRequest request) in " + className
            );
        }
    }
}
