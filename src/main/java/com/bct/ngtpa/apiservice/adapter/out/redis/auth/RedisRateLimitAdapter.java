package com.bct.ngtpa.apiservice.adapter.out.redis.auth;

import com.bct.ngtpa.apiservice.adapter.out.redis.RedisCacheKeyFactory;
import com.bct.ngtpa.apiservice.application.dto.security.ratelimit.RateLimitCommand;
import com.bct.ngtpa.apiservice.application.dto.security.ratelimit.RateLimitResult;
import com.bct.ngtpa.apiservice.application.port.out.RateLimitPort;
import com.bct.ngtpa.apiservice.exception.CacheException;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public class RedisRateLimitAdapter implements RateLimitPort {

    static final String INCREMENT_AND_TTL_SCRIPT_TEXT = "local current = redis.call('INCR', KEYS[1])\n"
            + "if current == 1 then\n"
            + "  redis.call('EXPIRE', KEYS[1], tonumber(ARGV[1]))\n"
            + "end\n"
            + "local ttl = redis.call('TTL', KEYS[1])\n"
            + "return { current, ttl }\n";

    @SuppressWarnings("rawtypes")
    private static final RedisScript<List> INCREMENT_AND_TTL_SCRIPT =
            RedisScript.of(INCREMENT_AND_TTL_SCRIPT_TEXT, List.class);

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final RedisCacheKeyFactory keyFactory;
    private final Clock clock;

    public RedisRateLimitAdapter(
            ReactiveRedisTemplate<String, String> redisTemplate,
            RedisCacheKeyFactory keyFactory) {
        this(redisTemplate, keyFactory, Clock.systemUTC());
    }

    public RedisRateLimitAdapter(
            ReactiveRedisTemplate<String, String> redisTemplate,
            RedisCacheKeyFactory keyFactory,
            Clock clock) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate must not be null");
        this.keyFactory = Objects.requireNonNull(keyFactory, "keyFactory must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public Mono<RateLimitResult> checkAndConsume(RateLimitCommand command) {
        try {
            validateCommand(command);
        } catch (RuntimeException exception) {
            return Mono.error(exception);
        }

        if (!command.enabled()) {
            return Mono.just(RateLimitResult.disabled(command.bucket()));
        }

        String redisKey = redisKey(command);
        return redisTemplate.execute(
                        INCREMENT_AND_TTL_SCRIPT,
                        List.of(redisKey),
                        List.of(String.valueOf(command.windowSeconds())))
                .next()
                .switchIfEmpty(Mono.error(new CacheException("Redis rate-limit operation failed")))
                .map(result -> toRateLimitResult(command, result))
                .onErrorMap(this::toSafeCacheException);
    }

    String redisKey(RateLimitCommand command) {
        return keyFactory.key("auth", "rate", command.bucket().name(), command.bucketId());
    }

    private void validateCommand(RateLimitCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Rate limit command must not be null");
        }
        if (command.bucket() == null) {
            throw new IllegalArgumentException("Rate limit bucket must not be null");
        }
        if (!command.enabled()) {
            return;
        }
        if (command.bucketId() == null || command.bucketId().isBlank()) {
            throw new IllegalArgumentException("Rate limit bucketId must not be blank when enabled");
        }
        if (command.maxAttempts() <= 0) {
            throw new IllegalArgumentException("Rate limit maxAttempts must be greater than zero when enabled");
        }
        if (command.windowSeconds() <= 0) {
            throw new IllegalArgumentException("Rate limit windowSeconds must be greater than zero when enabled");
        }
    }

    private RateLimitResult toRateLimitResult(RateLimitCommand command, List<?> scriptResult) {
        if (scriptResult == null || scriptResult.size() < 2) {
            throw new CacheException("Redis rate-limit operation failed");
        }

        long count = toLong(scriptResult.get(0));
        long ttlSeconds = toLong(scriptResult.get(1));
        if (count <= command.maxAttempts()) {
            int remaining = Math.max(0, command.maxAttempts() - Math.toIntExact(count));
            return RateLimitResult.allowed(
                    command.bucket(),
                    command.maxAttempts(),
                    remaining,
                    ttlSeconds > 0 ? now().plusSeconds(ttlSeconds) : null);
        }

        int retryAfterSeconds = ttlSeconds > 0 ? Math.toIntExact(ttlSeconds) : command.windowSeconds();
        return RateLimitResult.blocked(
                command.bucket(),
                command.maxAttempts(),
                retryAfterSeconds,
                now().plusSeconds(retryAfterSeconds));
    }

    private long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            return Long.parseLong(text);
        }
        throw new CacheException("Redis rate-limit operation failed");
    }

    private Instant now() {
        return Instant.now(clock);
    }

    private Throwable toSafeCacheException(Throwable throwable) {
        if (throwable instanceof CacheException) {
            return throwable;
        }
        return new CacheException("Redis rate-limit operation failed", throwable);
    }
}
