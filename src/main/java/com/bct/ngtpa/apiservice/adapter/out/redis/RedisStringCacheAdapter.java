package com.bct.ngtpa.apiservice.adapter.out.redis;

import com.bct.ngtpa.apiservice.application.port.out.CachePort;
import com.bct.ngtpa.apiservice.exception.CacheException;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Optional;

/**
 * Redis-backed implementation of {@link CachePort} using
 * {@link ReactiveRedisTemplate}{@code <String, String>} with plain UTF-8 string
 * serialization for both keys and values.
 *
 * <h3>Key contract</h3>
 * The adapter accepts fully-formed cache keys as-is — it does not add any
 * additional namespace prefix. Callers must supply keys that already include the
 * application prefix (e.g. {@code "ngtpa:reference-date:JP"}).
 *
 * <h3>Error mapping</h3>
 * Any infrastructure exception thrown by Lettuce / Spring Data Redis is caught
 * and mapped to {@link CacheException} with a safe, generic message. Raw error
 * messages, connection addresses, passwords, and certificate paths are never
 * propagated in the exception message; they remain accessible only via the
 * wrapped cause for secure diagnostic logging.
 */
public class RedisStringCacheAdapter implements CachePort {

    private final ReactiveRedisTemplate<String, String> redisTemplate;

    public RedisStringCacheAdapter(ReactiveRedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<Optional<String>> get(String cacheKey) {
        if (cacheKey == null || cacheKey.isBlank()) {
            return Mono.error(new IllegalArgumentException("Cache key must not be blank"));
        }
        return redisTemplate.opsForValue()
                .get(cacheKey)
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .onErrorMap(e -> new CacheException("Cache get operation failed", e));
    }

    @Override
    public Mono<Void> set(String cacheKey, String value, Duration ttl) {
        if (cacheKey == null || cacheKey.isBlank()) {
            return Mono.error(new IllegalArgumentException("Cache key must not be blank"));
        }
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            return Mono.error(new IllegalArgumentException(
                    "Cache TTL must be a positive duration"));
        }
        return redisTemplate.opsForValue()
                .set(cacheKey, value, ttl)
                .then()
                .onErrorMap(e -> new CacheException("Cache set operation failed", e));
    }

    @Override
    public Mono<Boolean> evict(String cacheKey) {
        if (cacheKey == null || cacheKey.isBlank()) {
            return Mono.error(new IllegalArgumentException("Cache key must not be blank"));
        }
        return redisTemplate.delete(cacheKey)
                .map(count -> count > 0)
                .onErrorMap(e -> new CacheException("Cache evict operation failed", e));
    }
}
