package com.bct.ngtpa.apiservice.adapter.out.redis.auth;

import com.bct.ngtpa.apiservice.adapter.out.redis.RedisCacheKeyFactory;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionCreateCommand;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionData;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowStatus;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort;
import com.bct.ngtpa.apiservice.application.port.out.CachePort;
import com.bct.ngtpa.apiservice.exception.CacheException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import reactor.core.publisher.Mono;

public class RedisAuthFlowSessionAdapter implements AuthFlowSessionPort {

    private final CachePort cachePort;
    private final RedisCacheKeyFactory keyFactory;
    private final RedisAuthFlowSessionJsonMapper jsonMapper;
    private final Clock clock;

    public RedisAuthFlowSessionAdapter(
            CachePort cachePort,
            RedisCacheKeyFactory keyFactory,
            RedisAuthFlowSessionJsonMapper jsonMapper) {
        this(cachePort, keyFactory, jsonMapper, Clock.systemUTC());
    }

    RedisAuthFlowSessionAdapter(
            CachePort cachePort,
            RedisCacheKeyFactory keyFactory,
            RedisAuthFlowSessionJsonMapper jsonMapper,
            Clock clock) {
        this.cachePort = Objects.requireNonNull(cachePort, "cachePort must not be null");
        this.keyFactory = Objects.requireNonNull(keyFactory, "keyFactory must not be null");
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public Mono<AuthFlowSession> create(AuthFlowSessionCreateCommand command) {
        return Mono.defer(() -> {
            AuthFlowSession session = new AuthFlowSession(
                    command.authFlowSessionId(),
                    command.scenario(),
                    command.initialStatus(),
                    command.csrfTokenHash(),
                    command.activeOtpChallengeId(),
                    command.data(),
                    command.createdAt(),
                    command.createdAt(),
                    command.expiresAt());
            return persist(session).thenReturn(session);
        }).onErrorMap(this::safeFailure);
    }

    @Override
    public Mono<Optional<AuthFlowSession>> load(AuthFlowSessionId authFlowSessionId) {
        return Mono.defer(() -> cachePort.get(keyFor(authFlowSessionId))
                .map(optionalJson -> optionalJson.map(jsonMapper::fromJson)))
                .onErrorMap(this::safeFailure);
    }

    @Override
    public Mono<AuthFlowSession> update(AuthFlowSession session) {
        return Mono.defer(() -> persist(session).thenReturn(session))
                .onErrorMap(this::safeFailure);
    }

    @Override
    public Mono<AuthFlowSession> touch(AuthFlowSessionId authFlowSessionId, Instant now) {
        return mutateExisting(authFlowSessionId, now, (session, timestamp) -> new AuthFlowSession(
                session.authFlowSessionId(),
                session.scenario(),
                session.status(),
                session.csrfTokenHash(),
                session.activeOtpChallengeId(),
                session.data(),
                session.createdAt(),
                timestamp,
                session.expiresAt()));
    }

    @Override
    public Mono<AuthFlowSession> complete(AuthFlowSessionId authFlowSessionId, Instant now) {
        return mutateExisting(authFlowSessionId, now, (session, timestamp) -> new AuthFlowSession(
                session.authFlowSessionId(),
                session.scenario(),
                AuthFlowStatus.COMPLETED,
                session.csrfTokenHash(),
                session.activeOtpChallengeId(),
                session.data(),
                session.createdAt(),
                timestamp,
                session.expiresAt()));
    }

    @Override
    public Mono<AuthFlowSession> cancel(AuthFlowSessionId authFlowSessionId, Instant now) {
        return mutateExisting(authFlowSessionId, now, (session, timestamp) -> new AuthFlowSession(
                session.authFlowSessionId(),
                session.scenario(),
                AuthFlowStatus.CANCELLED,
                session.csrfTokenHash(),
                session.activeOtpChallengeId(),
                session.data(),
                session.createdAt(),
                timestamp,
                session.expiresAt()));
    }

    @Override
    public Mono<Void> delete(AuthFlowSessionId authFlowSessionId) {
        return Mono.defer(() -> cachePort.evict(keyFor(authFlowSessionId)).then())
                .onErrorMap(this::safeFailure);
    }

    private Mono<AuthFlowSession> mutateExisting(AuthFlowSessionId id, Instant now, SessionMutation mutation) {
        Objects.requireNonNull(now, "now must not be null");
        return load(id)
                .flatMap(optional -> optional
                        .map(Mono::just)
                        .orElseGet(() -> Mono.error(new IllegalStateException("Auth flow session not found"))))
                .map(session -> mutation.apply(session, now))
                .flatMap(mutated -> persist(mutated, now).thenReturn(mutated));
    }

    private Mono<Void> persist(AuthFlowSession session) {
        return persist(session, Instant.now(clock));
    }

    private Mono<Void> persist(AuthFlowSession session, Instant ttlReferenceTime) {
        if (session == null) {
            return Mono.error(new IllegalArgumentException("Auth flow session must not be null"));
        }
        Duration ttl = ttlUntil(ttlReferenceTime, session.expiresAt());
        return cachePort.set(keyFor(session.authFlowSessionId()), jsonMapper.toJson(session), ttl);
    }

    private String keyFor(AuthFlowSessionId authFlowSessionId) {
        return RedisAuthFlowSessionKeyFactory.keyFor(keyFactory, authFlowSessionId);
    }

    private Duration ttlUntil(Instant referenceTime, Instant expiresAt) {
        Objects.requireNonNull(referenceTime, "referenceTime must not be null");
        if (expiresAt == null) {
            throw new IllegalArgumentException("Auth flow session expiresAt must not be null");
        }
        Duration ttl = Duration.between(referenceTime, expiresAt);
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("Auth flow session TTL must be positive");
        }
        return ttl;
    }

    private Throwable safeFailure(Throwable throwable) {
        if (throwable instanceof IllegalArgumentException || throwable instanceof IllegalStateException) {
            return throwable;
        }
        if (throwable instanceof CacheException cacheException) {
            return cacheException;
        }
        return new CacheException("Auth flow session persistence operation failed", throwable);
    }

    @FunctionalInterface
    private interface SessionMutation {
        AuthFlowSession apply(AuthFlowSession session, Instant now);
    }
}
