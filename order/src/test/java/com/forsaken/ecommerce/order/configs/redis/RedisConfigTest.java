package com.forsaken.ecommerce.order.configs.redis;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.nio.ByteBuffer;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;



/**
 * Loads only the Redis configuration and test-specific properties,
 * avoiding full application context startup.
 */
@SpringBootTest(
        classes = {
                RedisConfig.class,
                RedisConfigTest.TestRedisPropertiesConfig.class
        }
)
/**
 * Unit tests for {@link RedisConfig}.
 *
 * <p>This test suite validates the Redis cache configuration used by the service,
 * ensuring that client-side cache behavior is correctly defined and remains
 * stable over time.</p>
 *
 * <p>The tests intentionally focus on observable behavior rather than
 * Spring Data Redis internal implementation details. In particular, the
 * serialization test verifies round-trip correctness instead of asserting
 * on non-public framework classes.</p>
 *
 * <p>Verified aspects:</p>
 * <ul>
 *   <li>The {@link RedisCacheConfiguration} bean is created successfully</li>
 *   <li>The configured cache TTL is correctly applied</li>
 *   <li>Null values are not cached</li>
 *   <li>Cached values are serialized and deserialized using JSON</li>
 * </ul>
 *
 * <p>The test uses a test-specific {@link RedisProperties} configuration to
 * remain fast, deterministic, and independent of external configuration
 * sources.</p>
 */
class RedisConfigTest {

    @Autowired
    private RedisCacheConfiguration cacheConfiguration;

    @Autowired
    private RedisProperties redisProperties;

    /**
     * Verifies that the {@link RedisCacheConfiguration} bean
     * is successfully created by the Spring context.
     */
    @Test
    void shouldCreateRedisCacheConfigurationBean() {
        assertThat(cacheConfiguration).isNotNull();
    }

    /**
     * Ensures that the cache entry TTL is applied according to
     * the configured {@link RedisProperties}.
     */
    @Test
    void shouldApplyConfiguredTtl() {
        // Expected TTL derived from test RedisProperties
        final Duration expectedTtl = Duration.ofMinutes(redisProperties.ttlMinutes());

        // Assert that the cache configuration TTL matches expected value
        assertThat(cacheConfiguration.getTtl())
                .isEqualTo(expectedTtl);
    }

    /**
     * Confirms that {@code null} values are not cached,
     * preventing stale cache misses and unnecessary entries.
     */
    @Test
    void shouldDisableCachingOfNullValues() {
        // Assert that caching of null values is disabled
        assertThat(cacheConfiguration.getAllowCacheNullValues())
                .isFalse();
    }

    /**
     * Verifies that cache values are serialized and deserialized correctly
     * using JSON serialization.
     *
     * <p>This test performs a full round-trip serialization check instead of
     * asserting on Spring internal classes, which are intentionally package-private
     * and subject to change.</p>
     *
     * <p>The test guarantees that objects stored in Redis can be safely restored
     * without data loss or structural changes.</p>
     */
    @Test
    void shouldRoundTripSerializeAndDeserialize() {
        // Obtain the serialization pair from the cache configuration
        final RedisSerializationContext.SerializationPair<Object> pair =
                cacheConfiguration.getValueSerializationPair();
        // Extract the writer and reader
        final var writer = pair.getWriter();
        final var reader = pair.getReader();

        // Create a test DTO to serialize
        final TestDto dto = new TestDto("abc", 123);

        // Perform round-trip serialization and deserialization
        final byte[] bytes = writer.write(dto).array();
        // Deserialize back to an object
        final Object restored = reader.read(ByteBuffer.wrap(bytes));

        // Assert that the restored object matches the original DTO
        assertThat(restored)
                .usingRecursiveComparison()
                .isEqualTo(dto);
    }

    /**
     * Simple DTO used to verify cache serialization behavior.
     *
     * <p>This record intentionally contains multiple fields to ensure
     * structural integrity during serialization and deserialization.</p>
     */
    record TestDto(String name, int value) {}

    /**
     * Test-only RedisProperties configuration.
     * <p>
     * Avoids loading external configuration files and keeps
     * the test fast and deterministic.
     */
    @TestConfiguration
    static class TestRedisPropertiesConfig {

        @Bean
        RedisProperties redisProperties() {
            return new RedisProperties(15L);
        }
    }
}