package com.bct.ngtpa.apiservice.application.port.out;

import java.time.Instant;
import java.util.Objects;

/**
 * Persisted Username Recovery idempotency state with durable reservation ownership. Sensitive
 * values are always redacted from string output.
 */
public record UsernameRecoveryIdempotencyRecord(
    UsernameRecoveryIdempotencyAction action,
    UsernameRecoveryIdempotencyKey idempotencyKey,
    UsernameRecoveryIdempotencyFingerprint fingerprint,
    UsernameRecoveryIdempotencyOperationReference operationReference,
    Status status,
    ReplaySnapshot replaySnapshot,
    Instant createdAt,
    Instant updatedAt,
    Instant expiresAt) {

  public enum Status {
    IN_PROGRESS,
    COMPLETED,
    UNKNOWN_OUTCOME
  }

  public record ReplaySnapshot(String value) {
    public ReplaySnapshot {
      if (value == null || value.isBlank()) {
        throw new IllegalArgumentException("replay snapshot must not be null or blank");
      }
    }

    @Override
    public String toString() {
      return "ReplaySnapshot[redacted]";
    }
  }

  public UsernameRecoveryIdempotencyRecord {
    Objects.requireNonNull(action, "action must not be null");
    Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
    Objects.requireNonNull(fingerprint, "fingerprint must not be null");
    Objects.requireNonNull(operationReference, "operationReference must not be null");
    Objects.requireNonNull(status, "status must not be null");
    Objects.requireNonNull(createdAt, "createdAt must not be null");
    Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    updatedAt = updatedAt == null ? createdAt : updatedAt;
    if (!expiresAt.isAfter(createdAt)) {
      throw new IllegalArgumentException("expiresAt must be after createdAt");
    }
    if (status == Status.COMPLETED && replaySnapshot == null) {
      throw new IllegalArgumentException("completed record requires a replay snapshot");
    }
    if (status != Status.COMPLETED && replaySnapshot != null) {
      throw new IllegalArgumentException("non-completed record must not carry a replay snapshot");
    }
  }

  @Override
  public String toString() {
    return "UsernameRecoveryIdempotencyRecord[action="
        + action
        + ", status="
        + status
        + ", createdAt="
        + createdAt
        + ", updatedAt="
        + updatedAt
        + ", expiresAt="
        + expiresAt
        + ", idempotencyKey=, fingerprint=, operationReference=, replaySnapshot=]";
  }
}
