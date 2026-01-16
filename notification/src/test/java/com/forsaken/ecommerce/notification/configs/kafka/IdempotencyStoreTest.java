package com.forsaken.ecommerce.notification.configs.kafka;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link IdempotencyStore}.
 *
 * <p>This test suite verifies the correctness of Redis-based idempotency logic
 * used to guarantee effectively-once processing for Kafka consumers.</p>
 *
 * <p>The tests focus on:</p>
 * <ul>
 *   <li>Atomic event registration using Redis {@code SET NX EX}</li>
 *   <li>Correct Redis key construction using {@link IdempotencyScope}</li>
 *   <li>Proper handling of duplicate and retry scenarios</li>
 *   <li>Safe behavior when Redis returns {@code null}</li>
 *   <li>Correct cleanup of idempotency keys on failure</li>
 * </ul>
 *
 * <p>This is a pure unit test:
 * <ul>
 *   <li>No Spring context is started</li>
 *   <li>Redis is fully mocked using Mockito</li>
 *   <li>All interactions are verified explicitly</li>
 * </ul>
 *
 * <p>Mockito is used in {@code STRICT_STUBS} mode to ensure:
 * <ul>
 *   <li>No unnecessary stubbing</li>
 *   <li>Clear and maintainable test intent</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class IdempotencyStoreTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private IdempotencyStore idempotencyStore;

    private static final String EVENT_ID = "ORDER-123";
    private static final String PAYMENT_KEY = "payment-idempotency:ORDER-123";
    private static final String ORDER_KEY = "order-idempotency:ORDER-123";
    private static final Duration TTL = Duration.ofHours(24);

    /**
     * Verifies that {@link IdempotencyStore#markIfNotProcessed(IdempotencyScope, String)}
     * returns {@code true} when an event is seen for the first time.
     *
     * <p>This simulates Redis successfully creating the idempotency key using
     * {@code SET NX EX}.</p>
     *
     * <p>Expected behavior:</p>
     * <ul>
     *   <li>Redis is called with the correct key, value, and TTL</li>
     *   <li>The method returns {@code true}</li>
     * </ul>
     */
    @Test
    void shouldReturnTrue_whenEventIsProcessedForFirstTime_paymentScope() {
        // given
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(PAYMENT_KEY, "DONE", TTL))
                .thenReturn(true);

        // when
        boolean result = idempotencyStore.markIfNotProcessed(
                IdempotencyScope.PAYMENT,
                EVENT_ID
        );

        // then
        assertTrue(result);
        verify(valueOperations).setIfAbsent(PAYMENT_KEY, "DONE", TTL);
        verifyNoMoreInteractions(valueOperations);
    }

    /**
     * Verifies that {@link IdempotencyStore#markIfNotProcessed(IdempotencyScope, String)}
     * returns {@code false} when the event has already been processed.
     *
     * <p>This simulates Redis rejecting the operation because the key already exists.</p>
     *
     * <p>Expected behavior:</p>
     * <ul>
     *   <li>The existing idempotency key is not overwritten</li>
     *   <li>The method returns {@code false}</li>
     * </ul>
     */
    @Test
    void shouldReturnFalse_whenEventWasAlreadyProcessed_paymentScope() {
        // given
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(PAYMENT_KEY, "DONE", TTL))
                .thenReturn(false);

        // when
        final boolean result = idempotencyStore.markIfNotProcessed(
                IdempotencyScope.PAYMENT,
                EVENT_ID
        );

        // then
        assertFalse(result);
        verify(valueOperations).setIfAbsent(PAYMENT_KEY, "DONE", TTL);
        verifyNoMoreInteractions(valueOperations);
    }

    /**
     * Verifies defensive behavior when Redis returns {@code null}.
     *
     * <p>Although Redis typically returns {@code true} or {@code false},
     * network issues or client inconsistencies can lead to {@code null}.
     * The implementation must treat this as "not first time".</p>
     *
     * <p>Expected behavior:</p>
     * <ul>
     *   <li>No exception is thrown</li>
     *   <li>The method safely returns {@code false}</li>
     * </ul>
     */
    @Test
    void shouldReturnFalse_whenRedisReturnsNull() {
        // given
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(PAYMENT_KEY, "DONE", TTL))
                .thenReturn(null);

        // when
        final boolean result = idempotencyStore.markIfNotProcessed(
                IdempotencyScope.PAYMENT,
                EVENT_ID
        );

        // then
        assertFalse(result);
        verify(valueOperations).setIfAbsent(PAYMENT_KEY, "DONE", TTL);
    }

    /**
     * Verifies that different {@link IdempotencyScope} values result in different
     * Redis keys for the same event ID.
     *
     * <p>This prevents collisions between logically separate domains
     * (e.g., PAYMENT vs ORDER) that may share the same business identifier.</p>
     *
     * <p>Expected behavior:</p>
     * <ul>
     *   <li>Distinct Redis keys are used per scope</li>
     *   <li>Both operations are treated independently</li>
     * </ul>
     */
    @Test
    void shouldUseDifferentKeys_forDifferentScopes() {
        // given
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any()))
                .thenReturn(true);

        // when
        idempotencyStore.markIfNotProcessed(IdempotencyScope.PAYMENT, EVENT_ID);
        idempotencyStore.markIfNotProcessed(IdempotencyScope.ORDER, EVENT_ID);

        // then
        verify(valueOperations).setIfAbsent(PAYMENT_KEY, "DONE", TTL);
        verify(valueOperations).setIfAbsent(ORDER_KEY, "DONE", TTL);
    }

    /**
     * Verifies that the idempotency key is correctly removed for the PAYMENT scope.
     *
     * <p>This is used to rollback idempotency in failure scenarios,
     * allowing Kafka retries to reprocess the event.</p>
     */
    @Test
    void shouldDeleteKey_forPaymentScope() {
        // when
        idempotencyStore.remove(IdempotencyScope.PAYMENT, EVENT_ID);

        // then
        verify(redisTemplate).delete(PAYMENT_KEY);
        verifyNoMoreInteractions(redisTemplate);
    }

    /**
     * Verifies that the idempotency key is correctly removed for the ORDER scope.
     *
     * <p>This ensures that idempotency rollback is scope-aware and does not
     * affect unrelated domains.</p>
     */
    @Test
    void shouldDeleteKey_forOrderScope() {
        // when
        idempotencyStore.remove(IdempotencyScope.ORDER, EVENT_ID);

        // then
        verify(redisTemplate).delete(ORDER_KEY);
        verifyNoMoreInteractions(redisTemplate);
    }
}