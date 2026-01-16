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
     * Atomically marks the event as processed.
     *
     * @return true if this is the FIRST time we see this event
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