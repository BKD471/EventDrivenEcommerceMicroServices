package com.forsaken.ecommerce.order.order.service;

/**
 * Per-order saga recovery operations driven by the batch jobs. Every operation claims the order first,
 * so concurrent workers and instances never process the same order twice.
 */
public interface IOrderSagaRecoveryService {

    /**
     * Retries the compensation of an order in {@code COMPENSATION_REQUIRED}.
     *
     * @throws SagaRecoveryException if compensation is still incomplete
     */
    SagaRecoveryOutcome retryCompensation(Integer orderId);

    /**
     * Resumes an unfinished ({@code PENDING}/{@code CONFIRMING}) saga from its persisted state.
     *
     * @throws SagaRecoveryException if the saga could not be completed on this pass
     */
    SagaRecoveryOutcome recover(Integer orderId);
}
