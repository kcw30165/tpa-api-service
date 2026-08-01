package com.bct.ngtpa.apiservice.adapter.out.redis.auth.revision;

import com.bct.ngtpa.apiservice.exception.CacheException;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import reactor.core.publisher.Mono;

public final class ReactiveRevisionedAuthFlowSessionRedisOperations
        implements RevisionedAuthFlowSessionRedisOperations {
    private static final DefaultRedisScript<Long> CREATE = script(
            "redis/auth-flow-session/create-revisioned.lua");
    private static final DefaultRedisScript<Long> INITIALIZE = script(
            "redis/auth-flow-session/initialize-legacy-revision.lua");
    private final ReactiveStringRedisTemplate redis;

    public ReactiveRevisionedAuthFlowSessionRedisOperations(ReactiveStringRedisTemplate redis) {
        this.redis = Objects.requireNonNull(redis, "redis must not be null");
    }

    @Override
    public Mono<Boolean> createIfAbsent(String key, String json, Duration ttl) {
        requireText(key, "sessionKey"); requireText(json, "revisedJson"); requireTtl(ttl);
        return redis.execute(CREATE, List.of(key), List.of(json, Long.toString(ttl.toMillis())))
                .next().map(value -> value != null && value == 1L).defaultIfEmpty(false)
                .onErrorMap(error -> new CacheException("Revisioned auth-flow session create failed", error));
    }

    @Override
    public Mono<Long> initializeLegacy(String sessionKey, String lockKey, String token,
            String expectedLegacyJson, String revisedJson) {
        requireText(sessionKey, "sessionKey"); requireText(lockKey, "lockKey");
        requireText(token, "ownershipToken"); requireText(expectedLegacyJson, "expectedLegacyJson");
        requireText(revisedJson, "revisedJson");
        return redis.execute(INITIALIZE, List.of(sessionKey, lockKey),
                        List.of(token, expectedLegacyJson, revisedJson))
                .next().defaultIfEmpty(-4L)
                .onErrorMap(error -> new CacheException("Legacy auth-flow revision initialization failed", error));
    }

    private static DefaultRedisScript<Long> script(String path) {
        var script = new DefaultRedisScript<Long>();
        script.setLocation(new ClassPathResource(path));
        script.setResultType(Long.class);
        return script;
    }
    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    }
    private static void requireTtl(Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative())
            throw new IllegalArgumentException("ttl must be positive");
    }
}
