package com.bct.ngtpa.apiservice.application.port.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Verifies the Username Recovery idempotency reservation ownership artifact: it binds the action,
 * opaque key, semantic fingerprint, and the durable operation reference that proves which
 * concurrent caller owns the reservation, with a bounded expiry. {@link #toString()} never
 * discloses key, fingerprint, or operation reference.
 *
 * <p>Design references: {@code
 * docs/design/6-username-recovery/username-recovery-session-redis-design.md} section 4 (confirm
 * reservation ownership) and section 8 (safe persistence). Mirrors the opaque reservation ownership
 * conventions of the auth-flow idempotency ports.
 */
class UsernameRecoveryIdempotencyReservationTest {

  private static final Instant NOW = Instant.parse("2026-08-08T00:00:00Z");

  @Test
  void requiresAllFields() {
    assertThatThrownBy(
            () ->
                new UsernameRecoveryIdempotencyReservation(
                    null, key(), fingerprint(), operationReference(), NOW))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () ->
                new UsernameRecoveryIdempotencyReservation(
                    UsernameRecoveryIdempotencyAction.REQUEST,
                    null,
                    fingerprint(),
                    operationReference(),
                    NOW))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () ->
                new UsernameRecoveryIdempotencyReservation(
                    UsernameRecoveryIdempotencyAction.REQUEST,
                    key(),
                    null,
                    operationReference(),
                    NOW))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () ->
                new UsernameRecoveryIdempotencyReservation(
                    UsernameRecoveryIdempotencyAction.REQUEST, key(), fingerprint(), null, NOW))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () ->
                new UsernameRecoveryIdempotencyReservation(
                    UsernameRecoveryIdempotencyAction.REQUEST,
                    key(),
                    fingerprint(),
                    operationReference(),
                    null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void carriesOwnershipAndExpiry() {
    UsernameRecoveryIdempotencyReservation reservation =
        new UsernameRecoveryIdempotencyReservation(
            UsernameRecoveryIdempotencyAction.REQUEST,
            key(),
            fingerprint(),
            operationReference(),
            NOW.plusSeconds(300));

    assertThat(reservation.action()).isEqualTo(UsernameRecoveryIdempotencyAction.REQUEST);
    assertThat(reservation.idempotencyKey()).isEqualTo(key());
    assertThat(reservation.fingerprint()).isEqualTo(fingerprint());
    assertThat(reservation.operationReference()).isEqualTo(operationReference());
    assertThat(reservation.expiresAt()).isEqualTo(NOW.plusSeconds(300));
  }

  @Test
  void toStringRedactsKeyFingerprintAndOperationReference() {
    UsernameRecoveryIdempotencyReservation reservation =
        new UsernameRecoveryIdempotencyReservation(
            UsernameRecoveryIdempotencyAction.REQUEST,
            new UsernameRecoveryIdempotencyKey("idem_secret"),
            new UsernameRecoveryIdempotencyFingerprint(DIGEST),
            new UsernameRecoveryIdempotencyOperationReference("op-secret"),
            NOW.plusSeconds(300));

    assertThat(reservation.toString())
        .contains("action=REQUEST", "expiresAt=")
        .doesNotContain("idem_secret", DIGEST, "op-secret");
  }

  private static UsernameRecoveryIdempotencyKey key() {
    return new UsernameRecoveryIdempotencyKey("idem_12345678");
  }

  private static UsernameRecoveryIdempotencyFingerprint fingerprint() {
    return new UsernameRecoveryIdempotencyFingerprint(DIGEST);
  }

  private static UsernameRecoveryIdempotencyOperationReference operationReference() {
    return new UsernameRecoveryIdempotencyOperationReference("op-1234567890");
  }

  private static final String DIGEST =
      "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
}
