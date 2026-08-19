package com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ForgotPasswordIdempotencyContractTest {

  @Test
  void actionAndStatusVocabularyMatchFoundationContract() {
    assertThat(ForgotPasswordIdempotencyAction.values())
        .containsExactly(
            ForgotPasswordIdempotencyAction.START,
            ForgotPasswordIdempotencyAction.DETAILS_AND_OTP,
            ForgotPasswordIdempotencyAction.OTP_RESEND,
            ForgotPasswordIdempotencyAction.OTP_VERIFY,
            ForgotPasswordIdempotencyAction.COMPLETE,
            ForgotPasswordIdempotencyAction.CANCEL);
    assertThat(ForgotPasswordIdempotencyStatus.values())
        .containsExactly(
            ForgotPasswordIdempotencyStatus.PROCESSING,
            ForgotPasswordIdempotencyStatus.COMPLETED,
            ForgotPasswordIdempotencyStatus.FAILED,
            ForgotPasswordIdempotencyStatus.UNKNOWN_OUTCOME);
    assertThat(Arrays.stream(ForgotPasswordIdempotencyStatus.values()).map(Enum::name))
        .doesNotContain("DOWNSTREAM_REJECTED");
  }

  @Test
  void completedRecordRequiresSafeSnapshotAndRedactsSensitiveValues() {
    var record =
        new ForgotPasswordIdempotencyRecord(
            ForgotPasswordIdempotencyAction.OTP_VERIFY,
            new ForgotPasswordIdempotencyKey("idem-safe"),
            new ForgotPasswordRequestHash("hash-safe"),
            ForgotPasswordIdempotencyStatus.COMPLETED,
            new ForgotPasswordIdempotencyReplaySnapshot(
                "SUCCESS", "opaque-ref", Map.of("X-Request-Id", "req-1"), "STAFF_NO"),
            Instant.parse("2026-07-30T00:00:00Z"),
            Instant.parse("2026-07-30T00:00:01Z"),
            Instant.parse("2026-07-30T00:05:00Z"));

    assertThat(record.toString()).doesNotContain("idem-safe", "hash-safe", "opaque-ref");
    assertThat(record.replaySnapshot().toString()).doesNotContain("opaque-ref");
    assertThatThrownBy(
            () ->
                new ForgotPasswordIdempotencyRecord(
                    ForgotPasswordIdempotencyAction.START,
                    new ForgotPasswordIdempotencyKey("idem-safe"),
                    new ForgotPasswordRequestHash("hash-safe"),
                    ForgotPasswordIdempotencyStatus.COMPLETED,
                    null,
                    Instant.parse("2026-07-30T00:00:00Z"),
                    Instant.parse("2026-07-30T00:00:01Z"),
                    Instant.parse("2026-07-30T00:05:00Z")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("completed");
  }
}
