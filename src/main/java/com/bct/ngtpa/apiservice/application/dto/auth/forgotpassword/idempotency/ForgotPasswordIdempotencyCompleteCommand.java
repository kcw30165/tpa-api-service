package com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public record ForgotPasswordIdempotencyCompleteCommand(
        ForgotPasswordIdempotencyAction action,
        ForgotPasswordIdempotencyKey idempotencyKey,
        ForgotPasswordRequestHash requestHash,
        ForgotPasswordIdempotencyReplaySnapshot replaySnapshot,
        Instant completedAt,
        Duration ttl) {

    public ForgotPasswordIdempotencyCompleteCommand {
        Objects.requireNonNull(action, "action must not be null");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
        Objects.requireNonNull(requestHash, "requestHash must not be null");
        Objects.requireNonNull(replaySnapshot, "replaySnapshot must not be null");
        Objects.requireNonNull(completedAt, "completedAt must not be null");
        Objects.requireNonNull(ttl, "ttl must not be null");
    }
}