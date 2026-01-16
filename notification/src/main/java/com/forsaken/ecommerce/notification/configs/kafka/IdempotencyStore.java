package com.forsaken.ecommerce.notification.configs.kafka;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;


@Component
@RequiredArgsConstructor
public class IdempotencyStore {

    private static final Duration TTL = Duration.ofHours(24);
    private static final String VALUE = "DONE";

    private final RedisTemplate<String, String> redis;

    /**
     * Atomically marks the event as processed using a Redis-backed idempotency key.
     * <p>
     * A key is created for the given {@code scope} and {@code eventId} with a TTL of 24 hours.
     * If Redis is unavailable or {@code setIfAbsent} returns {@code null}, this method treats
     * the operation as not being the first time and returns {@code false}.
     *
     * @param scope   the idempotency scope that groups related events
     * @param eventId the unique identifier of the event within the given scope
     * @return {@code true} if this is the first time this event is observed; {@code false} otherwise
     */
    public boolean markIfNotProcessed(
            final IdempotencyScope scope,
            final String eventId
    ) {
        final Boolean firstTime = redis.opsForValue()
                .setIfAbsent(buildKey(scope, eventId), VALUE, TTL);

        return Boolean.TRUE.equals(firstTime);
    }

    /**
     * Removes the idempotency marker for the given event.
     * <p>
     * This is typically used as part of a rollback/compensation flow when
     * processing of an event fails after it has already been marked as processed
     * by {@link #markIfNotProcessed(IdempotencyScope, String)}. Removing the
     * marker allows the event to be safely retried and processed again.
     *
     * @param scope   logical scope used to namespace the idempotency key
     * @param eventId unique identifier of the event whose idempotency marker
     *                should be cleared
     */
    public void remove(final IdempotencyScope scope, final String eventId) {
        redis.delete(buildKey(scope, eventId));
    }

    /**
     * Builds a namespaced Redis key used for idempotency tracking.
     *
     * <p>
     * The generated key combines the logical {@link IdempotencyScope} with the
     * event identifier to ensure isolation between different event domains
     * (e.g. PAYMENT vs ORDER) while still allowing the same {@code eventId}
     * value to be reused safely across scopes.
     * </p>
     *
     * <p>
     * Key format:
     * </p>
     *
     * <pre>
     * &lt;scope&gt;-idempotency:&lt;eventId&gt;
     * </pre>
     *
     * <p>
     * Example:
     * </p>
     * <pre>
     * payment-idempotency:ORD-123
     * order-idempotency:ORD-123
     * </pre>
     *
     * <p>
     * This design prevents accidental key collisions in Redis and makes
     * idempotency keys easy to inspect and debug during operations.
     * </p>
     *
     * @param scope   logical scope used to namespace the idempotency key
     * @param eventId unique identifier of the event within the given scope
     * @return a fully-qualified Redis key for idempotency tracking
     */
    private String buildKey(final IdempotencyScope scope, final String eventId) {
        return scope.name().toLowerCase() + "-idempotency:" + eventId;
    }
}