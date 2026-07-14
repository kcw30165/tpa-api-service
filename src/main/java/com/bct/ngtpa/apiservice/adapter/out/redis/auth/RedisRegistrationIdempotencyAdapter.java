package com.bct.ngtpa.apiservice.adapter.out.redis.auth;

import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyAction;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyCompleteCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyFailCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyKey;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyRecord;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyStartCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyStartOutcome;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyStartResult;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyStatus;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyUnknownOutcomeCommand;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationIdempotencyPort;
import java.util.Optional;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;

public class RedisRegistrationIdempotencyAdapter implements RegistrationIdempotencyPort {
    private final RedisRegistrationIdempotencyKeyFactory keyFactory;
    private final RedisRegistrationIdempotencyJsonMapper jsonMapper;
    private final RedisRegistrationIdempotencyOperations redis;

    public RedisRegistrationIdempotencyAdapter(
            RedisRegistrationIdempotencyKeyFactory keyFactory,
            RedisRegistrationIdempotencyJsonMapper jsonMapper,
            ReactiveStringRedisTemplate redisTemplate) {
        this(keyFactory, jsonMapper, new ReactiveRedisRegistrationIdempotencyOperations(redisTemplate));
    }

    RedisRegistrationIdempotencyAdapter(
            RedisRegistrationIdempotencyKeyFactory keyFactory,
            RedisRegistrationIdempotencyJsonMapper jsonMapper,
            RedisRegistrationIdempotencyOperations redis) {
        this.keyFactory = keyFactory;
        this.jsonMapper = jsonMapper;
        this.redis = redis;
    }

    @Override
    public Mono<RegistrationIdempotencyStartResult> startProcessing(RegistrationIdempotencyStartCommand command) {
        String key = keyFactory.keyFor(command.action(), command.idempotencyKey());
        var record = new RegistrationIdempotencyRecord(
                command.action(),
                command.idempotencyKey(),
                command.requestHash(),
                RegistrationIdempotencyStatus.PROCESSING,
                null,
                command.now(),
                command.now(),
                command.expiresAt());
        return redis.setIfAbsent(key, jsonMapper.toJson(record), command.ttl())
                .flatMap(created -> created
                        ? Mono.just(new RegistrationIdempotencyStartResult(RegistrationIdempotencyStartOutcome.STARTED, record))
                        : loadExistingForStart(key, command))
                .onErrorMap(this::safeRedisFailure);
    }

    @Override
    public Mono<RegistrationIdempotencyRecord> markCompleted(RegistrationIdempotencyCompleteCommand command) {
        String key = keyFactory.keyFor(command.action(), command.idempotencyKey());
        return redis.get(key)
                .flatMap(optional -> optional
                        .map(json -> Mono.just(jsonMapper.fromJson(json)))
                        .orElseGet(() -> Mono.error(new IllegalStateException("Registration idempotency record missing"))))
                .flatMap(existing -> {
                    ensureSameRequestHash(existing, command.requestHash());
                    var completed = new RegistrationIdempotencyRecord(
                            existing.action(),
                            existing.idempotencyKey(),
                            existing.requestHash(),
                            RegistrationIdempotencyStatus.COMPLETED,
                            command.responseSnapshot(),
                            existing.createdAt(),
                            command.completedAt(),
                            command.completedAt().plus(command.ttl()));
                    return redis.set(key, jsonMapper.toJson(completed), command.ttl()).thenReturn(completed);
                })
                .onErrorMap(this::safeRedisFailureUnlessBusiness);
    }

    @Override
    public Mono<RegistrationIdempotencyRecord> markFailed(RegistrationIdempotencyFailCommand command) {
        String key = keyFactory.keyFor(command.action(), command.idempotencyKey());
        return redis.get(key)
                .flatMap(optional -> optional
                        .map(json -> Mono.just(jsonMapper.fromJson(json)))
                        .orElseGet(() -> Mono.error(new IllegalStateException("Registration idempotency record missing"))))
                .flatMap(existing -> {
                    ensureSameRequestHash(existing, command.requestHash());
                    var failed = new RegistrationIdempotencyRecord(
                            existing.action(),
                            existing.idempotencyKey(),
                            existing.requestHash(),
                            RegistrationIdempotencyStatus.FAILED,
                            null,
                            existing.createdAt(),
                            command.failedAt(),
                            command.failedAt().plus(command.ttl()));
                    return redis.set(key, jsonMapper.toJson(failed), command.ttl()).thenReturn(failed);
                })
                .onErrorMap(this::safeRedisFailureUnlessBusiness);
    }

    @Override
    public Mono<RegistrationIdempotencyRecord> markUnknownOutcome(
            RegistrationIdempotencyUnknownOutcomeCommand command) {
        String key = keyFactory.keyFor(command.action(), command.idempotencyKey());
        return redis.get(key)
                .flatMap(optional -> optional
                        .map(json -> Mono.just(jsonMapper.fromJson(json)))
                        .orElseGet(() -> Mono.error(new IllegalStateException(
                                "Registration idempotency record missing"))))
                .flatMap(existing -> {
                    ensureSameRequestHash(existing, command.requestHash());
                    if (existing.status() == RegistrationIdempotencyStatus.COMPLETED) {
                        return Mono.<RegistrationIdempotencyRecord>error(
                                new IllegalStateException(
                                        "Completed registration idempotency record cannot become unknown"));
                    }
                    var unknown = new RegistrationIdempotencyRecord(
                            existing.action(),
                            existing.idempotencyKey(),
                            existing.requestHash(),
                            RegistrationIdempotencyStatus.UNKNOWN_OUTCOME,
                            null,
                            existing.createdAt(),
                            command.recordedAt(),
                            command.recordedAt().plus(command.ttl()));
                    return redis.set(key, jsonMapper.toJson(unknown), command.ttl())
                            .thenReturn(unknown);
                })
                .onErrorMap(this::safeRedisFailureUnlessBusiness);
    }

    @Override
    public Mono<Optional<RegistrationIdempotencyRecord>> load(
            RegistrationIdempotencyAction action,
            RegistrationIdempotencyKey idempotencyKey) {
        String key = keyFactory.keyFor(action, idempotencyKey);
        return redis.get(key)
                .map(optional -> optional.map(jsonMapper::fromJson))
                .onErrorMap(this::safeRedisFailure);
    }

    @Override
    public Mono<Void> delete(RegistrationIdempotencyAction action, RegistrationIdempotencyKey idempotencyKey) {
        String key = keyFactory.keyFor(action, idempotencyKey);
        return redis.delete(key).onErrorMap(this::safeRedisFailure);
    }

    private Mono<RegistrationIdempotencyStartResult> loadExistingForStart(String key, RegistrationIdempotencyStartCommand command) {
        return redis.get(key)
                .flatMap(optional -> optional
                        .map(json -> Mono.just(jsonMapper.fromJson(json)))
                        .orElseGet(() -> Mono.error(new IllegalStateException("Registration idempotency record missing"))))
                .map(existing -> new RegistrationIdempotencyStartResult(outcomeForExisting(existing, command), existing));
    }

    private static RegistrationIdempotencyStartOutcome outcomeForExisting(
            RegistrationIdempotencyRecord existing,
            RegistrationIdempotencyStartCommand command) {
        if (!existing.requestHash().equals(command.requestHash())) {
            return RegistrationIdempotencyStartOutcome.KEY_REUSED_WITH_DIFFERENT_REQUEST;
        }
        return switch (existing.status()) {
            case PROCESSING -> RegistrationIdempotencyStartOutcome.DUPLICATE_IN_PROGRESS;
            case COMPLETED -> RegistrationIdempotencyStartOutcome.COMPLETED_REPLAY;
            case FAILED -> RegistrationIdempotencyStartOutcome.FAILED_EXISTING;
            case UNKNOWN_OUTCOME -> RegistrationIdempotencyStartOutcome.UNKNOWN_OUTCOME_EXISTING;
        };
    }

    private static void ensureSameRequestHash(RegistrationIdempotencyRecord existing, Object requestHash) {
        if (!existing.requestHash().equals(requestHash)) {
            throw new IllegalStateException("Registration idempotency request hash mismatch");
        }
    }

    private Throwable safeRedisFailureUnlessBusiness(Throwable throwable) {
        if (throwable instanceof IllegalStateException
                && ("Registration idempotency record missing".equals(throwable.getMessage())
                || "Registration idempotency request hash mismatch".equals(throwable.getMessage())
                || "Completed registration idempotency record cannot become unknown".equals(throwable.getMessage()))) {
            return throwable;
        }
        return safeRedisFailure(throwable);
    }

    private Throwable safeRedisFailure(Throwable throwable) {
        if (throwable instanceof IllegalStateException
                && ("Unable to deserialize registration idempotency record".equals(throwable.getMessage())
                || "Unable to serialize registration idempotency record".equals(throwable.getMessage())
                || "Registration idempotency record missing".equals(throwable.getMessage()))) {
            return throwable;
        }
        return new IllegalStateException("Redis registration idempotency operation failed");
    }
}
