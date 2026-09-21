package com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** Branch-oriented tests for {@link ForgotPasswordIdempotencyReplaySnapshot}'s compact constructor. */
class ForgotPasswordIdempotencyReplaySnapshotTest {

  @Test
  void nullOutcomeCodeThrows() {
    assertThatThrownBy(() -> new ForgotPasswordIdempotencyReplaySnapshot(null, "ref-1", Map.of()))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void blankOutcomeCodeThrows() {
    assertThatThrownBy(() -> new ForgotPasswordIdempotencyReplaySnapshot("", "ref-1", Map.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void blankOpaqueCommitRefThrows() {
    assertThatThrownBy(() -> new ForgotPasswordIdempotencyReplaySnapshot("SUCCESS", "  ", Map.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void nullHeadersBecomesEmptyMap() {
    ForgotPasswordIdempotencyReplaySnapshot snapshot =
        new ForgotPasswordIdempotencyReplaySnapshot("SUCCESS", "ref-1", null);
    assertThat(snapshot.safeHeaders()).isEmpty();
  }

  @Test
  void blankHeaderNameThrows() {
    Map<String, String> headers = new java.util.LinkedHashMap<>();
    headers.put("", "value");
    assertThatThrownBy(() -> new ForgotPasswordIdempotencyReplaySnapshot("SUCCESS", "ref-1", headers))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void blockedHeaderNameThrows() {
    assertThatThrownBy(
            () ->
                new ForgotPasswordIdempotencyReplaySnapshot(
                    "SUCCESS", "ref-1", Map.of("Set-Cookie", "session=abc")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void headerValueContainingCsrfMarkerThrows() {
    assertThatThrownBy(
            () ->
                new ForgotPasswordIdempotencyReplaySnapshot(
                    "SUCCESS", "ref-1", Map.of("X-Custom", "csrf_abc123")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void headerValueContainingBearerThrows() {
    assertThatThrownBy(
            () ->
                new ForgotPasswordIdempotencyReplaySnapshot(
                    "SUCCESS", "ref-1", Map.of("Authorization-Like", "Bearer xyz")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void validSafeHeadersAreRetained() {
    ForgotPasswordIdempotencyReplaySnapshot snapshot =
        new ForgotPasswordIdempotencyReplaySnapshot(
            "SUCCESS", "ref-1", Map.of("Retry-After", "30"));
    assertThat(snapshot.safeHeaders()).containsEntry("Retry-After", "30");
  }

  @Test
  void blankIdentityTypeBecomesNull() {
    ForgotPasswordIdempotencyReplaySnapshot snapshot =
        new ForgotPasswordIdempotencyReplaySnapshot("SUCCESS", "ref-1", Map.of(), "   ");
    assertThat(snapshot.identityType()).isNull();
  }

  @Test
  void fourArgConstructorDefaultsIdentityTypeToNull() {
    ForgotPasswordIdempotencyReplaySnapshot snapshot =
        new ForgotPasswordIdempotencyReplaySnapshot("SUCCESS", "ref-1", Map.of());
    assertThat(snapshot.identityType()).isNull();
  }

  @Test
  void toStringRedactsSensitiveFields() {
    ForgotPasswordIdempotencyReplaySnapshot snapshot =
        new ForgotPasswordIdempotencyReplaySnapshot(
            "SUCCESS", "sensitive-ref", Map.of(), "GPN");
    String rendered = snapshot.toString();
    assertThat(rendered).doesNotContain("sensitive-ref");
    assertThat(rendered).doesNotContain("GPN");
    assertThat(rendered).contains("SUCCESS");
  }
}
