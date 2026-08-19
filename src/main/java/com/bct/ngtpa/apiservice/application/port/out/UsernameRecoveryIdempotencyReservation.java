package com.bct.ngtpa.apiservice.application.port.out;

import java.time.Instant;
import java.util.Objects;

/**
 * Ownership artifact for a reserved Username Recovery idempotency operation.
 *
 * <p>Design references: {@code
 * docs/design/6-username-recovery/username-recovery-session-redis-design.md} section 4 (confirm
 * reservation ownership/idempotency operation) and section 8 (safe persistence). Only the caller
 * that presented this reservation's opaque operation reference may complete or record the unknown
 * outcome. {@link #toString()} never discloses key, fingerprint, or operation reference.
 */
public record UsernameRecoveryIdempotencyReservation(
    UsernameRecoveryIdempotencyAction action,
    UsernameRecoveryIdempotencyKey idempotencyKey,
    UsernameRecoveryIdempotencyFingerprint fingerprint,
    UsernameRecoveryIdempotencyOperationReference operationReference,
    Instant expiresAt) {

  public UsernameRecoveryIdempotencyReservation {
    Objects.requireNonNull(action, "action must not be null");
    Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
    Objects.requireNonNull(fingerprint, "fingerprint must not be null");
    Objects.requireNonNull(operationReference, "operationReference must not be null");
    Objects.requireNonNull(expiresAt, "expiresAt must not be null");
  }

  @Override
  public String toString() {
    return "UsernameRecoveryIdempotencyReservation[action="
        + action
        + ", expiresAt="
        + expiresAt
        + ", idempotencyKey=<redacted>, fingerprint=<redacted>, operationReference=<redacted>]";
  }
}
