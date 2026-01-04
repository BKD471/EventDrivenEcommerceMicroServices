package com.forsaken.ecommerce.payment.configs.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

/**
 * Redis cache configuration for the application.
 * <p>
 * This configuration defines default cache behavior such as:
 * <ul>
 *   <li>Global cache entry time-to-live (TTL)</li>
 *   <li>Serialization strategy for cached values</li>
 *   <li>Handling of null values</li>
 * </ul>
 * <p>
 * The TTL value is sourced from {@link RedisProperties} and applied
 * uniformly to all Redis caches unless explicitly overridden.
 */
@Configuration
@RequiredArgsConstructor
public class RedisConfig {

    private final RedisProperties redisProperties;

    /**
     * Creates the default {@link RedisCacheConfiguration} used by Spring Cache.
     * <p>
     * Configuration details:
     * <ul>
     *   <li>Applies a global TTL based on application cache settings</li>
     *   <li>Disables caching of {@code null} values to prevent stale misses</li>
     *   <li>Uses JSON serialization for cache values to ensure readability
     *       and cross-service compatibility</li>
     * </ul>
     *
     * @return configured {@link RedisCacheConfiguration} instance
     */
    @Bean
    public RedisCacheConfiguration cacheConfiguration() {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(redisProperties.ttlMinutes()))
                .disableCachingNullValues()
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                new GenericJackson2JsonRedisSerializer()
                        )
                );
    }
}