package com.bct.ngtpa.apiservice.adapter.out.redis.auth.login.otp;

import com.bct.ngtpa.apiservice.application.dto.auth.login.idempotency.*;
import com.bct.ngtpa.apiservice.application.port.out.LoginOtpSendIdempotencyPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import reactor.core.publisher.Mono;

public final class RedisLoginOtpSendIdempotencyAdapter implements LoginOtpSendIdempotencyPort {
    private final RedisLoginOtpSendIdempotencyKeyFactory keys;
    private final LoginOtpSendRedisOperations redis;
    private final ObjectMapper mapper = new ObjectMapper();

    public RedisLoginOtpSendIdempotencyAdapter(RedisLoginOtpSendIdempotencyKeyFactory keys,
            LoginOtpSendRedisOperations redis) {
        this.keys = Objects.requireNonNull(keys);
        this.redis = Objects.requireNonNull(redis);
    }

    public Mono<LoginOtpSendReservation> reserve(LoginOtpSendReserveCommand c) {
        String key = keys.keyFor(c.action(),c.idempotencyKey());
        Wire created = new Wire(c.requestHash().value(), "PROCESSING", null, c.now().toString(), c.now().toString(),
                c.now().plus(c.ttl()).toString());
        return redis.setIfAbsent(key, json(created), c.ttl())
                .flatMap(ok -> ok ? Mono.just(new LoginOtpSendReservation(LoginOtpSendReservationOutcome.STARTED, null))
                        : redis.get(key).map(v -> classify(read(v.orElseThrow()), c.requestHash())));
    }

    public Mono<Void> complete(LoginOtpSendCompleteCommand c) {
        return mutate(c.action(),c.idempotencyKey(), c.requestHash(), "COMPLETED", c.replay(), c.now(), c.ttl());
    }

    public Mono<Void> fail(LoginOtpSendTerminalCommand c) {
        return mutate(c.action(),c.idempotencyKey(), c.requestHash(), "FAILED", null, c.now(), c.ttl());
    }

    public Mono<Void> unknown(LoginOtpSendTerminalCommand c) {
        return mutate(c.action(),c.idempotencyKey(), c.requestHash(), "UNKNOWN", null, c.now(), c.ttl());
    }

    private Mono<Void> mutate(LoginIdempotencyAction action,String keyValue, LoginOtpSendRequestHash hash, String status,
            LoginOtpSendReplaySnapshot replay, Instant now, Duration ttl) {
        String key = keys.keyFor(action,keyValue);
        return redis.get(key).flatMap(v -> {
            Wire old = read(v.orElseThrow(() -> new IllegalStateException("OTP idempotency record missing")));
            if (!old.requestHash.equals(hash.value()))
                return Mono.error(new IllegalStateException("OTP idempotency hash mismatch"));
            return redis.set(key, json(
                    new Wire(old.requestHash, status, replay, old.createdAt, now.toString(), now.plus(ttl).toString())),
                    ttl);
        });
    }

    private LoginOtpSendReservation classify(Wire w, LoginOtpSendRequestHash hash) {
        if (!w.requestHash.equals(hash.value()))
            return new LoginOtpSendReservation(LoginOtpSendReservationOutcome.KEY_REUSED, null);
        return switch (w.status) {
            case "PROCESSING" ->
                new LoginOtpSendReservation(LoginOtpSendReservationOutcome.DUPLICATE_IN_PROGRESS, null);
            case "COMPLETED" -> new LoginOtpSendReservation(LoginOtpSendReservationOutcome.COMPLETED_REPLAY, w.replay);
            case "FAILED" -> new LoginOtpSendReservation(LoginOtpSendReservationOutcome.FAILED_EXISTING, null);
            default -> new LoginOtpSendReservation(LoginOtpSendReservationOutcome.UNKNOWN_OUTCOME, null);
        };
    }

    private String json(Wire w) {
        try {
            return mapper.writeValueAsString(w);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to serialize OTP idempotency record");
        }
    }

    private Wire read(String v) {
        try {
            return mapper.readValue(v, Wire.class);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to deserialize OTP idempotency record");
        }
    }

    private record Wire(String requestHash, String status, LoginOtpSendReplaySnapshot replay, String createdAt,
            String updatedAt, String expiresAt) {
    }
}
