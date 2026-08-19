package com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery;

import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyRecord;
import java.util.Objects;

/**
 * Secure CANCEL replay descriptor/codec (Task 021).
 *
 * <p>The completed CANCEL idempotency {@link UsernameRecoveryIdempotencyRecord.ReplaySnapshot}
 * carries a constant, versioned outcome descriptor with no session identifier and no
 * request-specific value. On a same-key completed replay, the cancel use case resolves the
 * descriptor and returns the idempotent cancellation success without loading or deleting the
 * session - deletion is never performed a second time. Only the exact constant decodes
 * successfully; malformed, unsupported, missing, unrelated, or contradictory completed replay state
 * fails closed and never deletes the session again to "repair" replay.
 *
 * <p>Design references: {@code
 * docs/design/6-username-recovery/username-recovery-api-contract-design.md} section 8
 * (UR-CANCEL-001 idempotent success for a valid or absent session) and {@code
 * .cline/tasks/021-cancel-use-case.md} "Replay descriptor" (prefer a constant versioned outcome
 * descriptor with no session identifier; the descriptor must not contain sensitive or
 * request-specific values). Session id, CSRF, cookie, destination, username, Scheme-Ref, identity,
 * DOB, key, fingerprint, and raw exception text are never part of the snapshot value.
 */
public final class UsernameRecoveryCancelReplayCodec {

  private static final String VALUE = "ur-cancel-completed:v1";

  private UsernameRecoveryCancelReplayCodec() {}

  /**
   * Encodes the constant non-secret completed CANCEL replay descriptor.
   *
   * @return the opaque replay snapshot whose value is exactly {@code ur-cancel-completed:v1}
   */
  public static UsernameRecoveryIdempotencyRecord.ReplaySnapshot encode() {
    return new UsernameRecoveryIdempotencyRecord.ReplaySnapshot(VALUE);
  }

  /**
   * Decodes a completed CANCEL replay snapshot.
   *
   * @param snapshot the persisted replay snapshot; null is treated as malformed
   * @return {@link DecodeResult#decoded()} for exactly the {@code ur-cancel-completed:v1} constant,
   *     or a typed {@code MALFORMED} result for null, blank, unrelated, wrong-version, or
   *     trailing-garbage values
   */
  public static DecodeResult decode(UsernameRecoveryIdempotencyRecord.ReplaySnapshot snapshot) {
    if (snapshot == null) {
      return new DecodeResult(DecodeResult.Status.MALFORMED);
    }
    return VALUE.equals(snapshot.value())
        ? new DecodeResult(DecodeResult.Status.DECODED)
        : new DecodeResult(DecodeResult.Status.MALFORMED);
  }

  /**
   * Typed decode outcome: {@code DECODED} for the exact constant; {@code MALFORMED} otherwise.
   * {@link #toString()} never discloses the snapshot value.
   */
  public record DecodeResult(Status status) {

    public enum Status {
      DECODED,
      MALFORMED
    }

    public DecodeResult {
      Objects.requireNonNull(status, "status must not be null");
    }

    public boolean decoded() {
      return status == Status.DECODED;
    }

    @Override
    public String toString() {
      return "DecodeResult[status=" + status + "]";
    }
  }
}
