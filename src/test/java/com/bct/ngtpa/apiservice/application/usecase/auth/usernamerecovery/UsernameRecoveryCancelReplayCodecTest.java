package com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyRecord;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyRecord.ReplaySnapshot;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.UsernameRecoveryCancelReplayCodec.DecodeResult;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Verifies the Task 021 {@link UsernameRecoveryCancelReplayCodec}.
 *
 * <p>The completed CANCEL replay snapshot is a constant versioned outcome descriptor with no
 * session identifier and no request-specific value (Task 021 "Replay descriptor"). Only the exact
 * constant {@code ur-cancel-completed:v1} decodes successfully; anything else - null, unrelated,
 * wrong version, trailing garbage, or foreign descriptors - fails closed as {@code MALFORMED}.
 */
class UsernameRecoveryCancelReplayCodecTest {

  private static final String CANCEL_DESCRIPTOR = "ur-cancel-completed:v1";

  @Test
  void encodeIsAClosedDescriptorWithoutSessionIdOrRequestValues() {
    ReplaySnapshot snapshot = UsernameRecoveryCancelReplayCodec.encode();

    assertThat(snapshot.value()).isEqualTo(CANCEL_DESCRIPTOR);
    assertThat(snapshot.value())
        .doesNotContain("afs_")
        .doesNotContain("csrf")
        .doesNotContain("cookie")
        .doesNotContain("idem_")
        .doesNotEndWith(":");
  }

  @Test
  void encodeIsStableAndRedacted() {
    ReplaySnapshot snapshot = UsernameRecoveryCancelReplayCodec.encode();
    ReplaySnapshot again = UsernameRecoveryCancelReplayCodec.encode();

    assertThat(again.value()).isEqualTo(snapshot.value());
    assertThat(snapshot.toString()).isEqualTo("ReplaySnapshot[redacted]");
  }

  @Test
  void decodeAcceptsTheExactConstantDescriptor() {
    DecodeResult decoded =
        UsernameRecoveryCancelReplayCodec.decode(UsernameRecoveryCancelReplayCodec.encode());

    assertThat(decoded.decoded()).isTrue();
    assertThat(decoded.status()).isEqualTo(DecodeResult.Status.DECODED);
  }

  @Test
  void decodeFailsClosedForNullSnapshot() {
    assertThat(UsernameRecoveryCancelReplayCodec.decode(null).decoded()).isFalse();
  }

  @Test
  void decodeFailsClosedForUnrelatedOrContradictoryDescriptors() {
    List<String> malformedValues =
        List.of(
            "ur-cancel-completed",
            "ur-cancel-completed:v2",
            "ur-cancel-completed:v1:extra",
            "ur-cancel-completed:v1 ",
            "ur-cancel-completed:v0",
            "ur-cancel-completed:v1.0",
            "ur-request:v1:afs_opaque-1",
            "ur-start-session-created:v1:afs_opaque-1",
            "some-other-value");

    for (String value : malformedValues) {
      DecodeResult decoded = UsernameRecoveryCancelReplayCodec.decode(new ReplaySnapshot(value));
      assertThat(decoded.decoded()).as("descriptor '%s' must fail closed", value).isFalse();
      assertThat(decoded.status())
          .as("descriptor '%s' must be MALFORMED", value)
          .isEqualTo(DecodeResult.Status.MALFORMED);
    }
  }

  @Test
  void decodeResultToStringNeverDisclosesTheSnapshot() {
    DecodeResult malformed =
        UsernameRecoveryCancelReplayCodec.decode(
            new ReplaySnapshot("ur-cancel-completed:v1:secret-session"));
    DecodeResult decoded =
        UsernameRecoveryCancelReplayCodec.decode(UsernameRecoveryCancelReplayCodec.encode());

    assertThat(malformed.toString()).isEqualTo("DecodeResult[status=MALFORMED]");
    assertThat(decoded.toString()).isEqualTo("DecodeResult[status=DECODED]");
    assertThat(malformed.toString()).doesNotContain("secret");
  }

  @Test
  void snapshotValueIsPersistedOnlyThroughTheOpaqueRecord() {
    UsernameRecoveryIdempotencyRecord record =
        new UsernameRecoveryIdempotencyRecord(
            com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyAction.CANCEL,
            new com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyKey(
                "idem_12345678"),
            new com.bct.ngtpa.apiservice.application.port.out
                .UsernameRecoveryIdempotencyFingerprint(
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"),
            new com.bct.ngtpa.apiservice.application.port.out
                .UsernameRecoveryIdempotencyOperationReference("op-ref"),
            com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyRecord.Status
                .COMPLETED,
            UsernameRecoveryCancelReplayCodec.encode(),
            java.time.Instant.parse("2026-01-01T00:00:00Z"),
            java.time.Instant.parse("2026-01-01T00:00:00Z"),
            java.time.Instant.parse("2026-01-01T00:10:00Z"));

    assertThat(record.toString())
        .doesNotContain("ur-cancel")
        .doesNotContain(CANCEL_DESCRIPTOR)
        .contains("replaySnapshot=");
  }
}
