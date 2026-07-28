package com.bct.ngtpa.apiservice.adapter.out.redis.auth;

import com.bct.ngtpa.apiservice.adapter.out.redis.RedisCacheKeyFactory;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaChallenge;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaChallengeId;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaStatus;
import com.bct.ngtpa.apiservice.application.port.out.CaptchaChallengePort;
import com.bct.ngtpa.apiservice.exception.CacheException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import reactor.core.publisher.Mono;

public class RedisCaptchaChallengeAdapter implements CaptchaChallengePort {

    private final RedisCacheKeyFactory keyFactory;
    private final RedisCaptchaChallengeJsonMapper jsonMapper;
    private final RedisCaptchaChallengeOperations redis;
    private final Clock clock;

    public RedisCaptchaChallengeAdapter(
            RedisCacheKeyFactory keyFactory,
            RedisCaptchaChallengeJsonMapper jsonMapper,
            ReactiveRedisTemplate<String, String> redisTemplate) {
        this(keyFactory, jsonMapper, new ReactiveRedisCaptchaChallengeOperations(redisTemplate), Clock.systemUTC());
    }

    RedisCaptchaChallengeAdapter(
            RedisCacheKeyFactory keyFactory,
            RedisCaptchaChallengeJsonMapper jsonMapper,
            RedisCaptchaChallengeOperations redis,
            Clock clock) {
        this.keyFactory = Objects.requireNonNull(keyFactory, "keyFactory must not be null");
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper must not be null");
        this.redis = Objects.requireNonNull(redis, "redis must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public Mono<CaptchaChallenge> create(CaptchaChallenge challenge) {
        return Mono.defer(() -> {
            Objects.requireNonNull(challenge, "challenge must not be null");
            Duration ttl = ttlUntil(challenge.expiresAt());
            String key = keyFor(challenge.captchaChallengeId());
            return redis.setIfAbsent(key, jsonMapper.toJson(challenge), ttl)
                    .flatMap(created -> created
                            ? Mono.just(challenge)
                            : Mono.error(new IllegalStateException("Captcha challenge already exists")));
        }).onErrorMap(this::safeFailure);
    }

    @Override
    public Mono<Optional<CaptchaChallenge>> load(CaptchaChallengeId captchaChallengeId) {
        return Mono.defer(() -> redis.get(keyFor(captchaChallengeId))
                .map(optionalJson -> optionalJson.map(jsonMapper::fromJson)))
                .onErrorMap(this::safeFailure);
    }

    @Override
    public Mono<CaptchaChallenge> markVerified(CaptchaChallengeId captchaChallengeId, Instant verifiedAt) {
        return mutateExisting(captchaChallengeId, challenge -> new CaptchaChallenge(
                challenge.captchaChallengeId(),
                challenge.answerHash(),
                CaptchaStatus.VERIFIED,
                challenge.failedAttemptCount(),
                challenge.maxAttempts(),
                challenge.createdAt(),
                challenge.expiresAt(),
                requireTimestamp(verifiedAt, "verifiedAt"),
                challenge.consumedAt(),
                challenge.refreshedAt(),
                challenge.lockedAt()));
    }

    @Override
    public Mono<CaptchaChallenge> incrementFailedAttempt(CaptchaChallengeId captchaChallengeId, Instant attemptedAt) {
        return mutateExisting(captchaChallengeId, challenge -> new CaptchaChallenge(
                challenge.captchaChallengeId(),
                challenge.answerHash(),
                challenge.status(),
                challenge.failedAttemptCount() + 1,
                challenge.maxAttempts(),
                challenge.createdAt(),
                challenge.expiresAt(),
                challenge.verifiedAt(),
                challenge.consumedAt(),
                challenge.refreshedAt(),
                challenge.lockedAt()));
    }

    @Override
    public Mono<CaptchaChallenge> markLocked(CaptchaChallengeId captchaChallengeId, Instant lockedAt) {
        return mutateExisting(captchaChallengeId, challenge -> new CaptchaChallenge(
                challenge.captchaChallengeId(),
                challenge.answerHash(),
                CaptchaStatus.LOCKED,
                challenge.failedAttemptCount(),
                challenge.maxAttempts(),
                challenge.createdAt(),
                challenge.expiresAt(),
                challenge.verifiedAt(),
                challenge.consumedAt(),
                challenge.refreshedAt(),
                requireTimestamp(lockedAt, "lockedAt")));
    }

    @Override
    public Mono<CaptchaChallenge> markConsumed(CaptchaChallengeId captchaChallengeId, Instant consumedAt) {
        return mutateExisting(captchaChallengeId, challenge -> new CaptchaChallenge(
                challenge.captchaChallengeId(),
                null,
                CaptchaStatus.CONSUMED,
                challenge.failedAttemptCount(),
                challenge.maxAttempts(),
                challenge.createdAt(),
                challenge.expiresAt(),
                challenge.verifiedAt(),
                requireTimestamp(consumedAt, "consumedAt"),
                challenge.refreshedAt(),
                challenge.lockedAt()));
    }

    @Override
    public Mono<CaptchaChallenge> markRefreshed(CaptchaChallengeId captchaChallengeId, Instant refreshedAt) {
        return mutateExisting(captchaChallengeId, challenge -> new CaptchaChallenge(
                challenge.captchaChallengeId(),
                null,
                CaptchaStatus.REFRESHED,
                challenge.failedAttemptCount(),
                challenge.maxAttempts(),
                challenge.createdAt(),
                challenge.expiresAt(),
                challenge.verifiedAt(),
                challenge.consumedAt(),
                requireTimestamp(refreshedAt, "refreshedAt"),
                challenge.lockedAt()));
    }

    @Override
    public Mono<Void> delete(CaptchaChallengeId captchaChallengeId) {
        return Mono.defer(() -> redis.delete(keyFor(captchaChallengeId))).onErrorMap(this::safeFailure);
    }

    private Mono<CaptchaChallenge> mutateExisting(CaptchaChallengeId captchaChallengeId, ChallengeMutation mutation) {
        return Mono.defer(() -> {
            String key = keyFor(captchaChallengeId);
            return redis.get(key)
                    .flatMap(optionalJson -> optionalJson
                            .map(json -> Mono.just(jsonMapper.fromJson(json)))
                            .orElseGet(() -> Mono.error(new IllegalStateException("Captcha challenge not found"))))
                    .flatMap(existing -> redis.ttl(key)
                            .map(optionalTtl -> optionalTtl.orElseGet(() -> ttlUntil(existing.expiresAt())))
                            .flatMap(ttl -> redis.set(key, jsonMapper.toJson(mutation.apply(existing)), ttl)
                                    .thenReturn(mutation.apply(existing))));
        }).onErrorMap(this::safeFailure);
    }

    private String keyFor(CaptchaChallengeId captchaChallengeId) {
        return RedisCaptchaChallengeKeyFactory.keyFor(keyFactory, captchaChallengeId);
    }

    private Duration ttlUntil(Instant expiresAt) {
        if (expiresAt == null) {
            throw new IllegalArgumentException("Captcha challenge expiresAt must not be null");
        }
        Duration ttl = Duration.between(Instant.now(clock), expiresAt);
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("Captcha challenge TTL must be positive");
        }
        return ttl;
    }

    private Instant requireTimestamp(Instant timestamp, String fieldName) {
        return Objects.requireNonNull(timestamp, fieldName + " must not be null");
    }

    private Throwable safeFailure(Throwable throwable) {
        if (throwable instanceof IllegalArgumentException || throwable instanceof IllegalStateException
                || throwable instanceof CacheException) {
            return throwable;
        }
        return new CacheException("Captcha challenge persistence operation failed", throwable);
    }

    @FunctionalInterface
    private interface ChallengeMutation {
        CaptchaChallenge apply(CaptchaChallenge challenge);
    }
}