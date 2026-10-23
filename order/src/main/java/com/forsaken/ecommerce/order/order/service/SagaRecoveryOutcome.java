package com.forsaken.ecommerce.order.order.service;

/** Result of a single-order saga recovery attempt that did not fail. */
public enum SagaRecoveryOutcome {
    /** The saga was driven to a terminal state. */
    RECOVERED,
    /** The saga could not progress right now and was left for a later run. */
    DEFERRED,
    /** Another worker holds the claim, or the order is no longer eligible. */
    NOT_CLAIMED
}
