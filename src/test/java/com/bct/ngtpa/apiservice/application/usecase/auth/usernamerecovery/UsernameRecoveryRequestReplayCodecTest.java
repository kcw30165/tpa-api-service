package com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyRecord;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.UsernameRecoveryRequestReplayCodec.DecodeResult;
import org.junit.jupiter.api.Test;

/**
 * Verifies the Task 020 request replay codec.
 *
 * <p>The completed REQUEST replay snapshot must persist only a non-secret, versioned replay
 * descriptor plus the protected session id - never the username, raw destination, CSRF, cookie,
 * identity, DOB, Scheme-Ref secret, or raw exception data. Decoding round-trips the session id and
 * fails closed on every malformed or unrelated snapshot.
 */
class UsernameRecoveryRequestReplayCodecTest {

  private static final AuthFlowSessionId SESSION_ID = new AuthFlowSessionId("afs_opaque-1");

  @Test
  void encodePersistsOnlyNonSecretDescriptorAndSessionId() {
    UsernameRecoveryIdempotencyRecord.ReplaySnapshot snapshot =
        UsernameRecoveryRequestReplayCodec.encode(SESSION_ID);

    assertThat(snapshot.value()).isEqualTo("ur-request:v1:afs_opaque-1");
  }

  @Test
  void encodedSnapshotContainsNoSensitivePayloadField() {
    String value = UsernameRecoveryRequestReplayCodec.encode(SESSION_ID).value();

    assertThat(value)
        .matches("ur-request:v1:[A-Za-z0-9_-]+")
        .doesNotContain(
            "username",
            "destination",
            "csrf",
            "token",
            "cookie",
            "identity",
            "dob",
            "schemeRef",
            "secret");
  }

  @Test
  void encodeRejectsNullSessionId() {
    assertThatThrownBy(() -> UsernameRecoveryRequestReplayCodec.encode(null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void decodeRoundTripsEncodedSnapshot() {
    DecodeResult result =
        UsernameRecoveryRequestReplayCodec.decode(
            UsernameRecoveryRequestReplayCodec.encode(SESSION_ID));

    assertThat(result.decoded()).isTrue();
    assertThat(result.authFlowSessionId()).isEqualTo(SESSION_ID);
  }

  @Test
  void decodeRejectsNullSnapshot() {
    DecodeResult result = UsernameRecoveryRequestReplayCodec.decode(null);

    assertThat(result.decoded()).isFalse();
    assertThat(result.authFlowSessionId()).isNull();
  }

  @Test
  void decodeRejectsUnrelatedDescriptor() {
    DecodeResult result =
        UsernameRecoveryRequestReplayCodec.decode(
            new UsernameRecoveryIdempotencyRecord.ReplaySnapshot(
                "ur-start-session-created:v1:afs_opaque-1"));

    assertThat(result.decoded()).isFalse();
  }

  @Test
  void decodeRejectsUnsupportedVersion() {
    DecodeResult result =
        UsernameRecoveryRequestReplayCodec.decode(
            new UsernameRecoveryIdempotencyRecord.ReplaySnapshot("ur-request:v2:afs_opaque-1"));

    assertThat(result.decoded()).isFalse();
  }

  @Test
  void decodeRejectsMissingVersion() {
    DecodeResult result =
        UsernameRecoveryRequestReplayCodec.decode(
            new UsernameRecoveryIdempotencyRecord.ReplaySnapshot("ur-request:afs_opaque-1"));

    assertThat(result.decoded()).isFalse();
  }

  @Test
  void decodeRejectsMissingSessionId() {
    DecodeResult result =
        UsernameRecoveryRequestReplayCodec.decode(
            new UsernameRecoveryIdempotencyRecord.ReplaySnapshot("ur-request:v1:"));

    assertThat(result.decoded()).isFalse();
  }

  @Test
  void decodeRejectsUnsafeSessionId() {
    DecodeResult result =
        UsernameRecoveryRequestReplayCodec.decode(
            new UsernameRecoveryIdempotencyRecord.ReplaySnapshot("ur-request:v1:bad value"));

    assertThat(result.decoded()).isFalse();
  }

  @Test
  void decodeRejectsTrailingGarbage() {
    DecodeResult result =
        UsernameRecoveryRequestReplayCodec.decode(
            new UsernameRecoveryIdempotencyRecord.ReplaySnapshot(
                "ur-request:v1:afs_opaque-1:extra"));

    assertThat(result.decoded()).isFalse();
  }

  @Test
  void encodedSnapshotToStringIsRedacted() {
    UsernameRecoveryIdempotencyRecord.ReplaySnapshot snapshot =
        UsernameRecoveryRequestReplayCodec.encode(SESSION_ID);

    assertThat(snapshot.toString()).isEqualTo("ReplaySnapshot[redacted]");
    assertThat(snapshot.toString()).doesNotContain("afs_opaque-1");
  }

  @Test
  void decodedResultToStringNeverDisclosesSessionId() {
    DecodeResult decoded =
        UsernameRecoveryRequestReplayCodec.decode(
            UsernameRecoveryRequestReplayCodec.encode(SESSION_ID));

    assertThat(decoded.toString()).contains("DECODED").doesNotContain("afs_opaque-1");
  }

  @Test
  void decodedResultRequiresSessionId() {
    assertThatThrownBy(() -> new DecodeResult(DecodeResult.Status.DECODED, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void malformedResultMustNotCarrySessionId() {
    assertThatThrownBy(() -> new DecodeResult(DecodeResult.Status.MALFORMED, SESSION_ID))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void decodeResultRequiresStatus() {
    assertThatThrownBy(() -> new DecodeResult(null, null)).isInstanceOf(NullPointerException.class);
  }
}
