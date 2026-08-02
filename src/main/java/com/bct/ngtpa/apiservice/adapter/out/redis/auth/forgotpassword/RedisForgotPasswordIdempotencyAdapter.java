package com.bct.ngtpa.apiservice.adapter.out.redis.auth.forgotpassword;

import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyAction;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyCompleteCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyFailCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyKey;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyRecord;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyStartCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyStartOutcome;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyStartResult;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyStatus;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyUnknownOutcomeCommand;
import com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordIdempotencyPort;
import java.util.Optional;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;

public final class RedisForgotPasswordIdempotencyAdapter implements ForgotPasswordIdempotencyPort {
    private final RedisForgotPasswordIdempotencyKeyFactory keyFactory;
    private final RedisForgotPasswordIdempotencyJsonMapper jsonMapper;
    private final RedisForgotPasswordIdempotencyOperations redis;

    public RedisForgotPasswordIdempotencyAdapter(
            RedisForgotPasswordIdempotencyKeyFactory keyFactory,
            RedisForgotPasswordIdempotencyJsonMapper jsonMapper,
            ReactiveStringRedisTemplate redisTemplate) {
        this(keyFactory, jsonMapper, new ReactiveRedisForgotPasswordIdempotencyOperations(redisTemplate));
    }

    RedisForgotPasswordIdempotencyAdapter(
            RedisForgotPasswordIdempotencyKeyFactory keyFactory,
            RedisForgotPasswordIdempotencyJsonMapper jsonMapper,
            RedisForgotPasswordIdempotencyOperations redis) {
        this.keyFactory = keyFactory;
        this.jsonMapper = jsonMapper;
        this.redis = redis;
    }

    @Override
    public Mono<com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordPendingCommitRefResult> attachPendingCommitRef(com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordPendingCommitRefCommand command) {
        if (command.action() != ForgotPasswordIdempotencyAction.DETAILS_AND_OTP
                && command.action() != ForgotPasswordIdempotencyAction.OTP_RESEND) return Mono.error(new IllegalStateException("Pending commit reference is not supported for this action"));
        String key=keyFactory.keyFor(command.action(),command.idempotencyKey());
        return redis.attachPendingCommitRef(key,command.requestHash().value(),command.opaqueCommitRef(),command.ttl())
                .map(jsonMapper::fromJson).map(r -> new com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordPendingCommitRefResult(
                        r.pendingOpaqueCommitRef().equals(command.opaqueCommitRef()) ? com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordPendingCommitRefResult.Status.ATTACHED : com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordPendingCommitRefResult.Status.EXISTING,
                        r.pendingOpaqueCommitRef())).onErrorMap(this::safeRedisFailureUnlessBusiness);
    }

    @Override
    public Mono<ForgotPasswordIdempotencyStartResult> startProcessing(ForgotPasswordIdempotencyStartCommand command) {
        String key = keyFactory.keyFor(command.action(), command.idempotencyKey());
        var record = new ForgotPasswordIdempotencyRecord(
                command.action(),
                command.idempotencyKey(),
                command.requestHash(),
                ForgotPasswordIdempotencyStatus.PROCESSING,
                null,
                command.now(),
                command.now(),
                command.expiresAt(),
                null);
        return redis.setIfAbsent(key, jsonMapper.toJson(record), command.ttl())
                .flatMap(created -> created
                        ? Mono.just(new ForgotPasswordIdempotencyStartResult(
                                ForgotPasswordIdempotencyStartOutcome.STARTED, record))
                        : loadExistingForStart(key, command))
                .onErrorMap(this::safeRedisFailure);
    }

    @Override
    public Mono<ForgotPasswordIdempotencyRecord> markCompleted(ForgotPasswordIdempotencyCompleteCommand command) {
        String key = keyFactory.keyFor(command.action(), command.idempotencyKey());
        return redis.get(key)
                .flatMap(optional -> optional
                        .map(json -> Mono.just(jsonMapper.fromJson(json)))
                        .orElseGet(() -> Mono
                                .error(new IllegalStateException("Forgot password idempotency record missing"))))
                .flatMap(existing -> {
                    ensureSameRequestHash(existing, command.requestHash());
                    var completed = new ForgotPasswordIdempotencyRecord(
                            existing.action(),
                            existing.idempotencyKey(),
                            existing.requestHash(),
                            ForgotPasswordIdempotencyStatus.COMPLETED,
                            command.replaySnapshot(),
                            existing.createdAt(),
                            command.completedAt(),
                            command.completedAt().plus(command.ttl()),
                            existing.pendingOpaqueCommitRef());
                    return redis.set(key, jsonMapper.toJson(completed), command.ttl()).thenReturn(completed);
                })
                .onErrorMap(this::safeRedisFailureUnlessBusiness);
    }

    @Override
    public Mono<ForgotPasswordIdempotencyRecord> markFailed(ForgotPasswordIdempotencyFailCommand command) {
        String key = keyFactory.keyFor(command.action(), command.idempotencyKey());
        return redis.get(key)
                .flatMap(optional -> optional
                        .map(json -> Mono.just(jsonMapper.fromJson(json)))
                        .orElseGet(() -> Mono
                                .error(new IllegalStateException("Forgot password idempotency record missing"))))
                .flatMap(existing -> {
                    ensureSameRequestHash(existing, command.requestHash());
                    var failed = new ForgotPasswordIdempotencyRecord(
                            existing.action(),
                            existing.idempotencyKey(),
                            existing.requestHash(),
                            ForgotPasswordIdempotencyStatus.FAILED,
                            null,
                            existing.createdAt(),
                            command.failedAt(),
                            command.failedAt().plus(command.ttl()),
                            existing.pendingOpaqueCommitRef());
                    return redis.set(key, jsonMapper.toJson(failed), command.ttl()).thenReturn(failed);
                })
                .onErrorMap(this::safeRedisFailureUnlessBusiness);
    }

    @Override
    public Mono<ForgotPasswordIdempotencyRecord> markUnknownOutcome(
            ForgotPasswordIdempotencyUnknownOutcomeCommand command) {
        String key = keyFactory.keyFor(command.action(), command.idempotencyKey());
        return redis.get(key)
                .flatMap(optional -> optional
                        .map(json -> Mono.just(jsonMapper.fromJson(json)))
                        .orElseGet(() -> Mono
                                .error(new IllegalStateException("Forgot password idempotency record missing"))))
                .flatMap(existing -> {
                    ensureSameRequestHash(existing, command.requestHash());
                    if (existing.status() == ForgotPasswordIdempotencyStatus.COMPLETED) {
                        return Mono.<ForgotPasswordIdempotencyRecord>error(
                                new IllegalStateException(
                                        "Completed forgot password idempotency record cannot become unknown"));
                    }
                    var unknown = new ForgotPasswordIdempotencyRecord(
                            existing.action(),
                            existing.idempotencyKey(),
                            existing.requestHash(),
                            ForgotPasswordIdempotencyStatus.UNKNOWN_OUTCOME,
                            null,
                            existing.createdAt(),
                            command.recordedAt(),
                            command.recordedAt().plus(command.ttl()),
                            existing.pendingOpaqueCommitRef());
                    return redis.set(key, jsonMapper.toJson(unknown), command.ttl()).thenReturn(unknown);
                })
                .onErrorMap(this::safeRedisFailureUnlessBusiness);
    }

    @Override
    public Mono<Optional<ForgotPasswordIdempotencyRecord>> load(
            ForgotPasswordIdempotencyAction action,
            ForgotPasswordIdempotencyKey idempotencyKey) {
        String key = keyFactory.keyFor(action, idempotencyKey);
        return redis.get(key).map(optional -> optional.map(jsonMapper::fromJson)).onErrorMap(this::safeRedisFailure);
    }

    @Override
    public Mono<Void> delete(ForgotPasswordIdempotencyAction action, ForgotPasswordIdempotencyKey idempotencyKey) {
        return redis.delete(keyFactory.keyFor(action, idempotencyKey)).onErrorMap(this::safeRedisFailure);
    }

    private Mono<ForgotPasswordIdempotencyStartResult> loadExistingForStart(
            String key,
            ForgotPasswordIdempotencyStartCommand command) {
        return redis.get(key)
                .flatMap(optional -> optional
                        .map(json -> Mono.just(jsonMapper.fromJson(json)))
                        .orElseGet(() -> Mono
                                .error(new IllegalStateException("Forgot password idempotency record missing"))))
                .map(existing -> new ForgotPasswordIdempotencyStartResult(outcomeForExisting(existing, command),
                        existing));
    }

    private static ForgotPasswordIdempotencyStartOutcome outcomeForExisting(
            ForgotPasswordIdempotencyRecord existing,
            ForgotPasswordIdempotencyStartCommand command) {
        if (!existing.requestHash().equals(command.requestHash())) {
            return ForgotPasswordIdempotencyStartOutcome.KEY_REUSED_WITH_DIFFERENT_REQUEST;
        }
        return switch (existing.status()) {
            case PROCESSING -> ForgotPasswordIdempotencyStartOutcome.DUPLICATE_IN_PROGRESS;
            case COMPLETED -> ForgotPasswordIdempotencyStartOutcome.COMPLETED_REPLAY;
            case FAILED -> ForgotPasswordIdempotencyStartOutcome.FAILED_EXISTING;
            case UNKNOWN_OUTCOME -> ForgotPasswordIdempotencyStartOutcome.UNKNOWN_OUTCOME_EXISTING;
        };
    }

    private static void ensureSameRequestHash(ForgotPasswordIdempotencyRecord existing, Object requestHash) {
        if (!existing.requestHash().equals(requestHash)) {
            throw new IllegalStateException("Forgot password idempotency request hash mismatch");
        }
    }

    private Throwable safeRedisFailureUnlessBusiness(Throwable throwable) {
        if (throwable instanceof IllegalStateException
                && ("Forgot password idempotency record missing".equals(throwable.getMessage())
                        || "Forgot password idempotency request hash mismatch".equals(throwable.getMessage())
                        || "Completed forgot password idempotency record cannot become unknown"
                                .equals(throwable.getMessage()))) {
            return throwable;
        }
        return safeRedisFailure(throwable);
    }

    private Throwable safeRedisFailure(Throwable throwable) {
        if (throwable instanceof IllegalStateException
                && ("Unable to deserialize forgot password idempotency record".equals(throwable.getMessage())
                        || "Unable to serialize forgot password idempotency record".equals(throwable.getMessage())
                        || "Forgot password idempotency record missing".equals(throwable.getMessage()))) {
            return throwable;
        }
        return new IllegalStateException("Redis forgot password idempotency operation failed");
    }
}