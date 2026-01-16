package com.forsaken.ecommerce.notification.configs.kafka;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class IdempotencyStore {

    private final RedisTemplate<String, String> redis;

    public boolean isAlreadyProcessed(final String id) {
        return Boolean.TRUE.equals(redis.hasKey(id));
    }

    public void markProcessed(final String id) {
        redis.opsForValue().set(id, "DONE", Duration.ofHours(24));
    }
}