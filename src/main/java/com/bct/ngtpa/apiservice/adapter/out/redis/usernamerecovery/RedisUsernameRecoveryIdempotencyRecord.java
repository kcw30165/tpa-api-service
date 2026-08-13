package com.bct.ngtpa.apiservice.adapter.out.redis.usernamerecovery;

import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyAction;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyFingerprint;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyKey;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyOperationReference;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyRecord;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyRecord.ReplaySnapshot;
import java.time.Instant;

/**
 * Minimal persistence record for the Username Recovery idempotency operation.
 *
 * <p>Persists only the Task 013 contract fields needed for reservation ownership,
 * semantic-fingerprint comparison, replay, and expiry: action, idempotency key,
 * semantic fingerprint, operation reference, status, replay snapshot (completed only),
 * and timestamps. Raw identity, DOB, destination, username, CSRF, cookie, Scheme-Ref,
 * captcha answers, request payloads, and HTTP data are never stored.
 *
 * <p>{@link #toString()} redacts the idempotency key, semantic fingerprint, operation
 * reference, and replay snapshot; serialized values must never appear in logs,
 * exceptions, or public string output.
 */
record RedisUsernameRecoveryIdempotencyRecord(
        String action,
        String idempotencyKey,
        String fingerprint,
        String operationReference,
        String status,
        String replaySnapshot,
        String createdAt,
        String updatedAt,
        String expiresAt) {

    static RedisUsernameRecoveryIdempotencyRecord from(UsernameRecoveryIdempotencyRecord record) {
        return new RedisUsernameRecoveryIdempotencyRecord(
                record.action().name(),
                record.idempotencyKey().value(),
                record.fingerprint().value(),
                record.operationReference().value(),
                record.status().name(),
                record.replaySnapshot() == null ? null : record.replaySnapshot().value(),
                record.createdAt().toString(),
                record.updatedAt().toString(),
                record.expiresAt().toString());
    }

    UsernameRecoveryIdempotencyRecord toRecord() {
        ReplaySnapshot snapshot = replaySnapshot == null
                ? null
                : new ReplaySnapshot(replaySnapshot);
        return new UsernameRecoveryIdempotencyRecord(
                UsernameRecoveryIdempotencyAction.valueOf(action),
                new UsernameRecoveryIdempotencyKey(idempotencyKey),
                new UsernameRecoveryIdempotencyFingerprint(fingerprint),
                new UsernameRecoveryIdempotencyOperationReference(operationReference),
                UsernameRecoveryIdempotencyRecord.Status.valueOf(status),
                snapshot,
                Instant.parse(createdAt),
                Instant.parse(updatedAt),
                Instant.parse(expiresAt));
    }

    @Override
    public String toString() {
        return "RedisUsernameRecoveryIdempotencyRecord[action=" + action
                + ", status=" + status
                + ", createdAt=" + createdAt
                + ", updatedAt=" + updatedAt
                + ", expiresAt=" + expiresAt
                + ", idempotencyKey=<redacted>, fingerprint=<redacted>"
                + ", operationReference=<redacted>, replaySnapshot=<redacted>]";
    }
}
