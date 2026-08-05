package com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public record ForgotPasswordIdempotencyFailCommand(
        ForgotPasswordIdempotencyAction action,
        ForgotPasswordIdempotencyKey idempotencyKey,
        ForgotPasswordRequestHash requestHash,
        Instant failedAt,
        Duration ttl) {

    public ForgotPasswordIdempotencyFailCommand {
        Objects.requireNonNull(action, "action must not be null");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
        Objects.requireNonNull(requestHash, "requestHash must not be null");
        Objects.requireNonNull(failedAt, "failedAt must not be null");
        Objects.requireNonNull(ttl, "ttl must not be null");
    }
}