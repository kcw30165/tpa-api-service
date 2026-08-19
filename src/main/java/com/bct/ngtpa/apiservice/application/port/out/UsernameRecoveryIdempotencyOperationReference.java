package com.bct.ngtpa.apiservice.application.port.out;

/**
 * Opaque reservation ownership reference for a Username Recovery idempotency reservation.
 *
 * <p>Design references: {@code
 * docs/design/6-username-recovery/username-recovery-session-redis-design.md} section 4 (confirm
 * reservation ownership/idempotency operation) and section 8 (safe persistence). The reference is a
 * durable, opaque claim token that proves which concurrent caller owns a reservation; the raw value
 * is never disclosed by {@link #toString()}.
 */
public record UsernameRecoveryIdempotencyOperationReference(String value) {

  public UsernameRecoveryIdempotencyOperationReference {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("operation reference must not be null or blank");
    }
  }

  @Override
  public String toString() {
    return "UsernameRecoveryIdempotencyOperationReference[redacted]";
  }
}
