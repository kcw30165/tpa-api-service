package com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency;

import java.time.Instant;
import java.util.Objects;

public record ForgotPasswordIdempotencyRecord(
        ForgotPasswordIdempotencyAction action,
        ForgotPasswordIdempotencyKey idempotencyKey,
        ForgotPasswordRequestHash requestHash,
        ForgotPasswordIdempotencyStatus status,
        ForgotPasswordIdempotencyReplaySnapshot replaySnapshot,
        Instant createdAt,
        Instant updatedAt,
        Instant expiresAt,
        String pendingOpaqueCommitRef) {

    public ForgotPasswordIdempotencyRecord(
            ForgotPasswordIdempotencyAction action,
            ForgotPasswordIdempotencyKey idempotencyKey,
            ForgotPasswordRequestHash requestHash,
            ForgotPasswordIdempotencyStatus status,
            ForgotPasswordIdempotencyReplaySnapshot replaySnapshot,
            Instant createdAt,
            Instant updatedAt,
            Instant expiresAt) {
        this(action, idempotencyKey, requestHash, status, replaySnapshot, createdAt, updatedAt, expiresAt, null);
    }

    public ForgotPasswordIdempotencyRecord {
        Objects.requireNonNull(action, "action must not be null");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
        Objects.requireNonNull(requestHash, "requestHash must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        updatedAt = updatedAt == null ? createdAt : updatedAt;
        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("expiresAt must be after createdAt");
        }
        if (status == ForgotPasswordIdempotencyStatus.COMPLETED && replaySnapshot == null) {
            throw new IllegalArgumentException("completed idempotency record requires replay snapshot");
        }
    }

    @Override
    public String toString() {
        return "ForgotPasswordIdempotencyRecord[action=" + action
                + ", status=" + status
                + ", createdAt=" + createdAt
                + ", updatedAt=" + updatedAt
                + ", expiresAt=" + expiresAt
                + ", idempotencyKey=<redacted>, requestHash=<redacted>, replaySnapshot=<redacted>, pendingOpaqueCommitRef=<redacted>]";
    }
}