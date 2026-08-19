package com.bct.ngtpa.apiservice.application.dto.auth.idempotency;

import java.time.Instant;
import java.util.Objects;

public record RegistrationIdempotencyRecord(
    RegistrationIdempotencyAction action,
    RegistrationIdempotencyKey idempotencyKey,
    RegistrationRequestHash requestHash,
    RegistrationIdempotencyStatus status,
    RegistrationIdempotencyResponseSnapshot responseSnapshot,
    Instant createdAt,
    Instant updatedAt,
    Instant expiresAt) {
  public RegistrationIdempotencyRecord {
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
    if (status == RegistrationIdempotencyStatus.COMPLETED && responseSnapshot == null) {
      throw new IllegalArgumentException("completed idempotency record requires response snapshot");
    }
  }

  @Override
  public String toString() {
    return "RegistrationIdempotencyRecord[action="
        + action
        + ", status="
        + status
        + ", createdAt="
        + createdAt
        + ", updatedAt="
        + updatedAt
        + ", expiresAt="
        + expiresAt
        + ", idempotencyKey=<redacted>, requestHash=<redacted>, responseSnapshot=<redacted>]";
  }
}
