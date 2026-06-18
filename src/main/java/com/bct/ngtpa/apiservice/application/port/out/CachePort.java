package com.bct.ngtpa.apiservice.application.port.out;

import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Optional;

/**
 * Outbound port for generic string-value cache operations.
 *
 * <p>This port exposes only application-facing types: no Redis, Lettuce,
 * Spring Data, or {@link java.util.concurrent.TimeUnit} types are used.
 *
 * <h3>Key contract</h3>
 * Callers are responsible for providing fully-formed, namespaced cache keys
 * (e.g. {@code "ngtpa:reference-date:JP"}). Blank or null keys are rejected
 * with {@link IllegalArgumentException} before any outbound call is made.
 *
 * <h3>TTL contract</h3>
 * A TTL is always required for {@link #set}. Null, zero, and negative durations
 * are rejected. This prevents accidental indefinite cache entries that could
 * serve stale data.
 *
 * <h3>Error contract</h3>
 * Infrastructure failures (connection errors, timeouts, Sentinel errors) are
 * mapped to {@link com.bct.ngtpa.apiservice.exception.CacheException} with a
 * safe generic message that does not leak connection details, passwords, or
 * certificate paths.
 */
public interface CachePort {

    /**
     * Retrieves the cached value for the given key.
     *
     * @param cacheKey fully-formed cache key; must not be blank.
     * @return {@code Mono<Optional.empty()>} when the key is absent,
     *         {@code Mono<Optional.of(value)>} when present.
     */
    Mono<Optional<String>> get(String cacheKey);

    /**
     * Writes a string value to the cache with the given TTL.
     *
     * @param cacheKey fully-formed cache key; must not be blank.
     * @param value    the value to store.
     * @param ttl      expiry duration; must not be null, zero, or negative.
     * @return a {@code Mono<Void>} that completes on success.
     */
    Mono<Void> set(String cacheKey, String value, Duration ttl);

    /**
     * Removes the entry for the given key.
     *
     * @param cacheKey fully-formed cache key; must not be blank.
     * @return {@code Mono<true>} if the key existed and was deleted,
     *         {@code Mono<false>} if the key was not found.
     */
    Mono<Boolean> evict(String cacheKey);
}
