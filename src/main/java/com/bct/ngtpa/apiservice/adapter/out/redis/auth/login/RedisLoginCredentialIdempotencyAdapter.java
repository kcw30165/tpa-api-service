package com.bct.ngtpa.apiservice.adapter.out.redis.auth.login;

import com.bct.ngtpa.apiservice.application.dto.auth.login.idempotency.*;
import com.bct.ngtpa.apiservice.application.port.out.LoginCredentialIdempotencyPort;
import com.bct.ngtpa.apiservice.application.service.auth.login.idempotency.LoginIdempotencyPersistenceIdDeriver;
import com.fasterxml.jackson.databind.*;
import java.time.*;
import java.util.*;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;

public final class RedisLoginCredentialIdempotencyAdapter implements LoginCredentialIdempotencyPort {
    private final RedisLoginIdempotencyKeyFactory keys;
    private final LoginIdempotencyPersistenceIdDeriver ids;
    private final ReactiveStringRedisTemplate redis;
    private final ObjectMapper mapper = new ObjectMapper();

    public RedisLoginCredentialIdempotencyAdapter(RedisLoginIdempotencyKeyFactory k,
            LoginIdempotencyPersistenceIdDeriver ids, ReactiveStringRedisTemplate r) {
        keys = k;
        this.ids = ids;
        redis = r;
    }

    public Mono<LoginIdempotencyStartResult> startProcessing(LoginIdempotencyStartCommand c) {
        var r = new LoginIdempotencyRecord(c.action(), c.requestHash(), LoginIdempotencyStatus.PROCESSING, null,
                c.now(), c.now(), c.expiresAt());
        String k = key(c.action(), c.idempotencyKey());
        return redis.opsForValue().setIfAbsent(k, json(r), c.ttl())
                .flatMap(created -> created
                        ? Mono.just(new LoginIdempotencyStartResult(LoginIdempotencyStartOutcome.STARTED, r))
                        : load(c.action(), c.idempotencyKey())
                                .map(o -> new LoginIdempotencyStartResult(outcome(o.orElseThrow(), c.requestHash()),
                                        o.orElseThrow())));
    }

    public Mono<LoginIdempotencyRecord> markCompleted(LoginIdempotencyCompleteCommand c) {
        return mutate(c.action(), c.idempotencyKey(), c.requestHash(), c.completedAt(), c.ttl(),
                LoginIdempotencyStatus.COMPLETED, c.replaySnapshot());
    }

    public Mono<LoginIdempotencyRecord> markFailed(LoginIdempotencyFailCommand c) {
        return mutate(c.action(), c.idempotencyKey(), c.requestHash(), c.failedAt(), c.ttl(),
                LoginIdempotencyStatus.FAILED, null);
    }

    public Mono<LoginIdempotencyRecord> markUnknownOutcome(LoginIdempotencyUnknownOutcomeCommand c) {
        return mutate(c.action(), c.idempotencyKey(), c.requestHash(), c.recordedAt(), c.ttl(),
                LoginIdempotencyStatus.UNKNOWN_OUTCOME, null);
    }

    public Mono<Optional<LoginIdempotencyRecord>> load(LoginIdempotencyAction a, LoginIdempotencyKey k) {
        return redis.opsForValue().get(key(a, k)).map(v -> Optional.of(fromJson(v))).defaultIfEmpty(Optional.empty());
    }

    public Mono<Void> delete(LoginIdempotencyAction a, LoginIdempotencyKey k) {
        return redis.delete(key(a, k)).then();
    }

    private String key(LoginIdempotencyAction a, LoginIdempotencyKey k) {
        return keys.keyFor(a, ids.derive(a, k));
    }

    private Mono<LoginIdempotencyRecord> mutate(LoginIdempotencyAction a, LoginIdempotencyKey k,
            LoginCredentialRequestHash h, Instant at, Duration ttl, LoginIdempotencyStatus st,
            LoginIdempotencyReplaySnapshot snap) {
        return load(a, k).flatMap(o -> {
            var old = o.orElseThrow(() -> new IllegalStateException("Login idempotency record missing"));
            if (!old.requestHash().equals(h))
                return Mono.error(new IllegalStateException("Login idempotency request hash mismatch"));
            var n = new LoginIdempotencyRecord(a, h, st, snap, old.createdAt(), at, old.expiresAt());
            Duration remaining = Duration.between(at, old.expiresAt());
            if (remaining.isZero() || remaining.isNegative())
                return Mono.error(new IllegalStateException("Login idempotency record expired"));
            return redis.opsForValue().set(key(a, k), json(n), remaining).thenReturn(n);
        });
    }

    private LoginIdempotencyStartOutcome outcome(LoginIdempotencyRecord r, LoginCredentialRequestHash h) {
        if (!r.requestHash().equals(h))
            return LoginIdempotencyStartOutcome.KEY_REUSED_WITH_DIFFERENT_REQUEST;
        return switch (r.status()) {
            case PROCESSING -> LoginIdempotencyStartOutcome.DUPLICATE_IN_PROGRESS;
            case COMPLETED -> LoginIdempotencyStartOutcome.COMPLETED_REPLAY;
            case FAILED -> LoginIdempotencyStartOutcome.FAILED_EXISTING;
            case UNKNOWN_OUTCOME -> LoginIdempotencyStartOutcome.UNKNOWN_OUTCOME_EXISTING;
        };
    }

    private String json(LoginIdempotencyRecord r) {
        try {
            return mapper.writeValueAsString(new Wire(r.action().name(), r.requestHash().value(), r.status().name(),
                    r.replaySnapshot(), r.createdAt().toString(), r.updatedAt().toString(), r.expiresAt().toString()));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to serialize login idempotency record");
        }
    }

    private LoginIdempotencyRecord fromJson(String v) {
        try {
            Wire w = mapper.readValue(v, Wire.class);
            return new LoginIdempotencyRecord(LoginIdempotencyAction.valueOf(w.action),
                    new LoginCredentialRequestHash(w.hash), LoginIdempotencyStatus.valueOf(w.status), w.snapshot,
                    Instant.parse(w.created), Instant.parse(w.updated), Instant.parse(w.expires));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to deserialize login idempotency record");
        }
    }

    private record Wire(String action, String hash, String status, LoginIdempotencyReplaySnapshot snapshot,
            String created, String updated, String expires) {
    }
}
