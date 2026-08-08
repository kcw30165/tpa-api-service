package com.bct.ngtpa.apiservice.adapter.out.redis.usernamerecovery;

import com.bct.ngtpa.apiservice.adapter.out.redis.RedisCacheKeyFactory;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoverySessionDeleteResult;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoverySessionLoadResult;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoverySessionMutationCommand;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoverySessionMutationOutcome;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoverySessionMutationResult;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoverySessionPort;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySession;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySessionId;
import com.bct.ngtpa.apiservice.exception.CacheException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import reactor.core.publisher.Mono;

/** Redis persistence adapter for the protected Username Recovery session. */
public class RedisUsernameRecoverySessionAdapter implements UsernameRecoverySessionPort {

    private static final DefaultRedisScript<Long> CREATE =
            script("redis/auth-flow-session/create-revisioned.lua");
    private static final DefaultRedisScript<Long> UPDATE =
            script("redis/username-recovery-session/update.lua");
    private static final DefaultRedisScript<Long> DELETE =
            script("redis/username-recovery-session/delete.lua");

    private final ReactiveStringRedisTemplate redis;
    private final RedisCacheKeyFactory keyFactory;
    private final RedisUsernameRecoverySessionJsonMapper jsonMapper;
    private final Clock clock;

    public RedisUsernameRecoverySessionAdapter(
            ReactiveStringRedisTemplate redis,
            RedisCacheKeyFactory keyFactory,
            RedisUsernameRecoverySessionJsonMapper jsonMapper) {
        this(redis, keyFactory, jsonMapper, Clock.systemUTC());
    }

    RedisUsernameRecoverySessionAdapter(
            ReactiveStringRedisTemplate redis,
            RedisCacheKeyFactory keyFactory,
            RedisUsernameRecoverySessionJsonMapper jsonMapper,
            Clock clock) {
        this.redis = Objects.requireNonNull(redis, "redis must not be null");
        this.keyFactory = Objects.requireNonNull(keyFactory, "keyFactory must not be null");
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public Mono<UsernameRecoverySessionMutationResult> create(UsernameRecoverySession session) {
        return Mono.defer(() -> {
                    Objects.requireNonNull(session, "session must not be null");
                    Duration ttl = remainingTtl(session.expiresAt());
                    String key = keyFor(session.sessionId());
                    String json = jsonMapper.toJson(session);
                    return redis.execute(
                                    CREATE,
                                    List.of(key),
                                    List.of(json, Long.toString(ttl.toMillis())))
                            .next()
                            .map(code -> mapCreateCode(code, session))
                            .defaultIfEmpty(systemFailureMutation());
                })
                .onErrorMap(error -> persistenceFailure("create", error));
    }

    @Override
    public Mono<UsernameRecoverySessionLoadResult> load(UsernameRecoverySessionId sessionId) {
        return Mono.defer(() -> {
                    Objects.requireNonNull(sessionId, "sessionId must not be null");
                    return redis.opsForValue()
                            .get(keyFor(sessionId))
                            .map(json -> UsernameRecoverySessionLoadResult.loaded(jsonMapper.fromJson(json)))
                            .defaultIfEmpty(UsernameRecoverySessionLoadResult.notFound());
                })
                .onErrorMap(error -> persistenceFailure("load", error));
    }

    @Override
    public Mono<UsernameRecoverySessionMutationResult> update(
            UsernameRecoverySessionMutationCommand command) {
        return Mono.defer(() -> {
                    Objects.requireNonNull(command, "command must not be null");
                    String revisedJson = jsonMapper.toJson(command.updatedSession());
                    return redis.execute(
                                    UPDATE,
                                    List.of(keyFor(command.sessionId())),
                                    List.of(
                                            Long.toString(command.expectedRevision().value()),
                                            revisedJson))
                            .next()
                            .map(code -> mapUpdateCode(code, command.updatedSession()))
                            .defaultIfEmpty(systemFailureMutation());
                })
                .onErrorMap(error -> persistenceFailure("update", error));
    }

    @Override
    public Mono<UsernameRecoverySessionDeleteResult> delete(UsernameRecoverySessionId sessionId) {
        return Mono.defer(() -> {
                    Objects.requireNonNull(sessionId, "sessionId must not be null");
                    return redis.execute(DELETE, List.of(keyFor(sessionId)), List.of())
                            .next()
                            .map(RedisUsernameRecoverySessionAdapter::mapDeleteCode)
                            .defaultIfEmpty(systemFailureDelete());
                })
                .onErrorMap(error -> persistenceFailure("delete", error));
    }

    private String keyFor(UsernameRecoverySessionId sessionId) {
        return RedisUsernameRecoverySessionKeyFactory.keyFor(keyFactory, sessionId);
    }

    private Duration remainingTtl(Instant expiresAt) {
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        Duration ttl = Duration.between(clock.instant(), expiresAt);
        if (ttl.isZero() || ttl.isNegative()) {
            throw new CacheException("Username recovery session create failed");
        }
        return ttl;
    }

    private static UsernameRecoverySessionMutationResult mapCreateCode(
            Long code, UsernameRecoverySession session) {
        if (code != null && code == 1L) {
            return new UsernameRecoverySessionMutationResult(
                    UsernameRecoverySessionMutationOutcome.CREATED, session);
        }
        return systemFailureMutation();
    }

    private static UsernameRecoverySessionMutationResult mapUpdateCode(
            Long code, UsernameRecoverySession updated) {
        if (code == null) {
            return systemFailureMutation();
        }
        return switch (code.intValue()) {
            case 1 -> new UsernameRecoverySessionMutationResult(
                    UsernameRecoverySessionMutationOutcome.APPLIED, updated);
            case -1, -3 -> UsernameRecoverySessionMutationResult.outcome(
                    UsernameRecoverySessionMutationOutcome.SESSION_NOT_FOUND);
            case 0 -> UsernameRecoverySessionMutationResult.outcome(
                    UsernameRecoverySessionMutationOutcome.REVISION_CONFLICT);
            default -> systemFailureMutation();
        };
    }

    private static UsernameRecoverySessionDeleteResult mapDeleteCode(Long code) {
        if (code == null) {
            return systemFailureDelete();
        }
        return switch (code.intValue()) {
            case 1 -> new UsernameRecoverySessionDeleteResult(
                    UsernameRecoverySessionMutationOutcome.DELETED);
            case -1 -> new UsernameRecoverySessionDeleteResult(
                    UsernameRecoverySessionMutationOutcome.ALREADY_ABSENT);
            default -> systemFailureDelete();
        };
    }

    private static UsernameRecoverySessionMutationResult systemFailureMutation() {
        return UsernameRecoverySessionMutationResult.outcome(
                UsernameRecoverySessionMutationOutcome.SYSTEM_FAILURE);
    }

    private static UsernameRecoverySessionDeleteResult systemFailureDelete() {
        return new UsernameRecoverySessionDeleteResult(
                UsernameRecoverySessionMutationOutcome.SYSTEM_FAILURE);
    }

    private static CacheException persistenceFailure(String operation, Throwable error) {
        if (error instanceof CacheException cacheException) {
            return cacheException;
        }
        return new CacheException(
                "Username recovery session " + operation + " failed", error);
    }

    private static DefaultRedisScript<Long> script(String path) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(path));
        script.setResultType(Long.class);
        return script;
    }
}
