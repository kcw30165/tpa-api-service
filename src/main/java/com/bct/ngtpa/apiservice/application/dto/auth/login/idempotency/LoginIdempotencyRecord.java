package com.bct.ngtpa.apiservice.application.dto.auth.login.idempotency;

import java.time.Instant;
import java.util.Objects;

public record LoginIdempotencyRecord(LoginIdempotencyAction action, LoginIdempotencyKey idempotencyKey,
        LoginCredentialRequestHash requestHash, LoginIdempotencyStatus status,
        LoginIdempotencyReplaySnapshot replaySnapshot, Instant createdAt, Instant updatedAt, Instant expiresAt) {
    public LoginIdempotencyRecord {
        Objects.requireNonNull(action);
        Objects.requireNonNull(idempotencyKey);
        Objects.requireNonNull(requestHash);
        Objects.requireNonNull(status);
        Objects.requireNonNull(createdAt);
        Objects.requireNonNull(expiresAt);
        updatedAt = updatedAt == null ? createdAt : updatedAt;
        if (!expiresAt.isAfter(createdAt))
            throw new IllegalArgumentException("expiresAt must follow createdAt");
        if (status == LoginIdempotencyStatus.COMPLETED && replaySnapshot == null)
            throw new IllegalArgumentException("completed record requires replay snapshot");
    }

    @Override
    public String toString() {
        return "LoginIdempotencyRecord[action=" + action + ", status=" + status
                + ", idempotencyKey=<redacted>, requestHash=<redacted>, replaySnapshot=<redacted>]";
    }
}
