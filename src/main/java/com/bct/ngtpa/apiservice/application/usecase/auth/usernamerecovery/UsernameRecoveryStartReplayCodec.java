package com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery;

import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyRecord;
import java.util.Objects;

/**
 * Secure START replay descriptor/codec (Repair 019B, Candidate A).
 *
 * <p>Establishes the completed START replay contract without ever persisting a raw CSRF token: the
 * idempotency {@link UsernameRecoveryIdempotencyRecord.ReplaySnapshot} carries only a non-secret,
 * versioned replay descriptor plus the protected session id. On a same-key completed replay, the
 * START use case (Task 019) decodes the session id, loads the protected session, rotates the CSRF
 * hash through expected-revision compare-and-set without extending the TTL, and returns a fresh raw
 * token - never a stored one. A decoded session id is required; a malformed or unrelated snapshot
 * fails closed and never triggers blind creation of a second session.
 *
 * <p>Design references: {@code
 * docs/design/6-username-recovery/username-recovery-session-redis-design.md} section 4 (replay
 * snapshot stored through {@code complete}) and {@code
 * docs/design/6-username-recovery/username-recovery-api-contract-design.md} section 8 (same-key
 * completed replay returns the original 200-shaped result). Raw CSRF, Base64-encoded tokens, and
 * other protected session data are never part of the snapshot value.
 */
public final class UsernameRecoveryStartReplayCodec {

  private static final String DESCRIPTOR = "ur-start-session-created";
  private static final String VERSION = "v1";
  private static final String SEPARATOR = ":";
  private static final String PREFIX = DESCRIPTOR + SEPARATOR + VERSION + SEPARATOR;

  private UsernameRecoveryStartReplayCodec() {}

  /**
   * Encodes the protected session id into the non-secret completed START replay snapshot.
   *
   * @param authFlowSessionId the protected session identifier; never null
   * @return the opaque replay snapshot whose value is exactly {@code
   *     ur-start-session-created:v1:<authFlowSessionId>}
   */
  public static UsernameRecoveryIdempotencyRecord.ReplaySnapshot encode(
      AuthFlowSessionId authFlowSessionId) {
    Objects.requireNonNull(authFlowSessionId, "authFlowSessionId must not be null");
    return new UsernameRecoveryIdempotencyRecord.ReplaySnapshot(PREFIX + authFlowSessionId.value());
  }

  /**
   * Decodes a completed START replay snapshot into the protected session id.
   *
   * @param snapshot the persisted replay snapshot; null is treated as malformed
   * @return {@link DecodeResult#decoded()} with the session id for exactly the {@code
   *     ur-start-session-created:v1:<authFlowSessionId>} shape, or a typed {@code MALFORMED} result
   *     for null, blank, unrelated, unsupported-version, missing-version, missing-session-id,
   *     unsafe-session-id, or trailing-garbage values
   */
  public static DecodeResult decode(UsernameRecoveryIdempotencyRecord.ReplaySnapshot snapshot) {
    if (snapshot == null) {
      return DecodeResult.malformed();
    }
    String value = snapshot.value();
    if (!value.startsWith(PREFIX)) {
      return DecodeResult.malformed();
    }
    try {
      return DecodeResult.decoded(new AuthFlowSessionId(value.substring(PREFIX.length())));
    } catch (IllegalArgumentException exception) {
      // Only the session-id validation failure is malformed input; unexpected runtime
      // defects must propagate instead of being silently converted into MALFORMED.
      return DecodeResult.malformed();
    }
  }

  /**
   * Typed decode outcome: {@code DECODED} carries the session id; {@code MALFORMED} carries none.
   * {@link #toString()} never discloses the session id.
   */
  public record DecodeResult(Status status, AuthFlowSessionId authFlowSessionId) {

    public enum Status {
      DECODED,
      MALFORMED
    }

    public DecodeResult {
      Objects.requireNonNull(status, "status must not be null");
      boolean decoded = status == Status.DECODED;
      if (decoded && authFlowSessionId == null) {
        throw new IllegalArgumentException("decoded result requires a authFlowSessionId");
      }
      if (!decoded && authFlowSessionId != null) {
        throw new IllegalArgumentException("malformed result must not carry a authFlowSessionId");
      }
    }

    public static DecodeResult decoded(AuthFlowSessionId authFlowSessionId) {
      return new DecodeResult(Status.DECODED, authFlowSessionId);
    }

    public static DecodeResult malformed() {
      return new DecodeResult(Status.MALFORMED, null);
    }

    public boolean decoded() {
      return status == Status.DECODED;
    }

    @Override
    public String toString() {
      return "DecodeResult[status="
          + status
          + ", authFlowSessionId="
          + (authFlowSessionId == null ? null : "<redacted>")
          + "]";
    }
  }
}
