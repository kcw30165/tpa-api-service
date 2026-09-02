package com.bct.ngtpa.apiservice.application.idempotency;

import java.time.Instant;
import java.util.Objects;

/** Shared session-context-neutral persistence record. */
public record IdempotencyRecord(
    IdempotencyOperationId operationId,
    IdempotencyFingerprint fingerprint,
    IdempotencyStatus status,
    IdempotentTerminalResponseSnapshot terminalResponse,
    Instant createdAt,
    Instant updatedAt,
    Instant expiresAt) {

  public IdempotencyRecord {
    Objects.requireNonNull(operationId, "operation id must not be null");
    Objects.requireNonNull(fingerprint, "fingerprint must not be null");
    Objects.requireNonNull(status, "status must not be null");
    Objects.requireNonNull(createdAt, "createdAt must not be null");
    Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    if (updatedAt.isBefore(createdAt) || !expiresAt.isAfter(updatedAt)) {
      throw new IllegalArgumentException("idempotency record timestamps are invalid");
    }
    if (status == IdempotencyStatus.COMPLETED && terminalResponse == null) {
      throw new IllegalArgumentException("completed idempotency record requires terminal response");
    }
    if (status != IdempotencyStatus.COMPLETED && terminalResponse != null) {
      throw new IllegalArgumentException("non-completed idempotency record cannot carry terminal response");
    }
  }
}
