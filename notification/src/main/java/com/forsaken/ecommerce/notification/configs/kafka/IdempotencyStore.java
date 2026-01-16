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

    public void remove(final IdempotencyScope scope, final String eventId) {
        redis.delete(buildKey(scope, eventId));
    }

    private String buildKey(final IdempotencyScope scope, final String eventId) {
        return scope.name().toLowerCase() + "-idempotency:" + eventId;
    }
}