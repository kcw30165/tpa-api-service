package com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyRecord;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.UsernameRecoveryStartReplayCodec.DecodeResult;
import org.junit.jupiter.api.Test;

/**
 * Verifies the Repair 019B secure START replay codec (Candidate A).
 *
 * <p>The completed START replay snapshot must persist only a non-secret, versioned replay
 * descriptor plus the protected session id; it must never persist, Base64-encode, log, or carry a
 * raw CSRF token. Encoding produces exactly the fixed {@code
 * ur-start-session-created:v1:<sessionId>} shape, and decoding round-trips the session id while
 * failing closed on every malformed or unrelated snapshot.
 *
 * <p>Design references: {@code
 * docs/design/6-username-recovery/username-recovery-session-redis-design.md} section 4 (replay
 * snapshot stored through {@code complete}) and {@code
 * docs/design/6-username-recovery/username-recovery-api-contract-design.md} section 8 (same-key
 * completed replay returns the original 200-shaped result; the START use case rotates the CSRF hash
 * and returns a fresh raw token).
 */
class UsernameRecoveryStartReplayCodecTest {

  private static final AuthFlowSessionId AUTH_FLOW_SESSION_ID =
      new AuthFlowSessionId("afs_opaque-1");

  @Test
  void encodePersistsOnlyNonSecretDescriptorAndSessionId() {
    UsernameRecoveryIdempotencyRecord.ReplaySnapshot snapshot =
        UsernameRecoveryStartReplayCodec.encode(AUTH_FLOW_SESSION_ID);

    assertThat(snapshot.value()).isEqualTo("ur-start-session-created:v1:afs_opaque-1");
  }

  @Test
  void encodedSnapshotContainsNoCsrfPayloadField() {
    String value = UsernameRecoveryStartReplayCodec.encode(AUTH_FLOW_SESSION_ID).value();

    assertThat(value)
        .matches("ur-start-session-created:v1:[A-Za-z0-9_-]+")
        .doesNotContain("csrf", "token", "base64", "secret");
  }

  @Test
  void encodeRejectsNullSessionId() {
    assertThatThrownBy(() -> UsernameRecoveryStartReplayCodec.encode(null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void decodeRoundTripsEncodedSnapshot() {
    DecodeResult result =
        UsernameRecoveryStartReplayCodec.decode(
            UsernameRecoveryStartReplayCodec.encode(AUTH_FLOW_SESSION_ID));

    assertThat(result.decoded()).isTrue();
    assertThat(result.authFlowSessionId()).isEqualTo(AUTH_FLOW_SESSION_ID);
  }

  @Test
  void decodeRejectsNullSnapshot() {
    DecodeResult result = UsernameRecoveryStartReplayCodec.decode(null);

    assertThat(result.decoded()).isFalse();
    assertThat(result.authFlowSessionId()).isNull();
  }

  // A blank snapshot is unrepresentable by the committed contract: the ReplaySnapshot
  // invariant rejects blank values at construction (UsernameRecoveryIdempotencyRecord), so
  // decode() can only ever receive null or a nonblank value.

  @Test
  void decodeRejectsUnrelatedDescriptor() {
    DecodeResult result =
        UsernameRecoveryStartReplayCodec.decode(
            new UsernameRecoveryIdempotencyRecord.ReplaySnapshot(
                "username-recovery-request:v1:afs_opaque-1"));

    assertThat(result.decoded()).isFalse();
  }

  @Test
  void decodeRejectsUnsupportedVersion() {
    DecodeResult result =
        UsernameRecoveryStartReplayCodec.decode(
            new UsernameRecoveryIdempotencyRecord.ReplaySnapshot(
                "ur-start-session-created:v2:afs_opaque-1"));

    assertThat(result.decoded()).isFalse();
  }

  @Test
  void decodeRejectsMissingVersion() {
    DecodeResult result =
        UsernameRecoveryStartReplayCodec.decode(
            new UsernameRecoveryIdempotencyRecord.ReplaySnapshot(
                "ur-start-session-created:afs_opaque-1"));

    assertThat(result.decoded()).isFalse();
  }

  @Test
  void decodeRejectsMissingSessionId() {
    DecodeResult result =
        UsernameRecoveryStartReplayCodec.decode(
            new UsernameRecoveryIdempotencyRecord.ReplaySnapshot("ur-start-session-created:v1:"));

    assertThat(result.decoded()).isFalse();
  }

  @Test
  void decodeRejectsUnsafeSessionId() {
    DecodeResult result =
        UsernameRecoveryStartReplayCodec.decode(
            new UsernameRecoveryIdempotencyRecord.ReplaySnapshot(
                "ur-start-session-created:v1:bad value"));

    assertThat(result.decoded()).isFalse();
  }

  @Test
  void decodeRejectsTrailingGarbage() {
    DecodeResult result =
        UsernameRecoveryStartReplayCodec.decode(
            new UsernameRecoveryIdempotencyRecord.ReplaySnapshot(
                "ur-start-session-created:v1:afs_opaque-1:extra"));

    assertThat(result.decoded()).isFalse();
  }

  @Test
  void encodedSnapshotToStringIsRedacted() {
    UsernameRecoveryIdempotencyRecord.ReplaySnapshot snapshot =
        UsernameRecoveryStartReplayCodec.encode(AUTH_FLOW_SESSION_ID);

    assertThat(snapshot.toString()).isEqualTo("ReplaySnapshot[redacted]");
    assertThat(snapshot.toString()).doesNotContain("afs_opaque-1");
  }

  @Test
  void decodedResultToStringNeverDisclosesSessionId() {
    DecodeResult decoded =
        UsernameRecoveryStartReplayCodec.decode(
            UsernameRecoveryStartReplayCodec.encode(AUTH_FLOW_SESSION_ID));

    assertThat(decoded.toString()).contains("DECODED").doesNotContain("afs_opaque-1");
  }

  @Test
  void decodedResultRequiresSessionId() {
    assertThatThrownBy(() -> new DecodeResult(DecodeResult.Status.DECODED, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void malformedResultMustNotCarrySessionId() {
    assertThatThrownBy(() -> new DecodeResult(DecodeResult.Status.MALFORMED, AUTH_FLOW_SESSION_ID))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void decodeResultRequiresStatus() {
    assertThatThrownBy(() -> new DecodeResult(null, null)).isInstanceOf(NullPointerException.class);
  }
}
