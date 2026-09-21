package com.bct.ngtpa.apiservice.application.dto.auth.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Branch-oriented tests for {@link RegistrationIdempotencyResponseSnapshot}'s compact constructor
 * - near-identical sibling of {@code ForgotPasswordIdempotencyReplaySnapshotTest} (round 81),
 * confirmed via direct source comparison (round 82).
 */
class RegistrationIdempotencyResponseSnapshotTest {

  @Test
  void nullStatusThrows() {
    assertThatThrownBy(() -> new RegistrationIdempotencyResponseSnapshot(null, "body", Map.of()))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void blankStatusThrows() {
    assertThatThrownBy(() -> new RegistrationIdempotencyResponseSnapshot("", "body", Map.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void nullHeadersBecomesEmptyMap() {
    RegistrationIdempotencyResponseSnapshot snapshot =
        new RegistrationIdempotencyResponseSnapshot("SUCCESS", "body", null);
    assertThat(snapshot.safeHeaders()).isEmpty();
  }

  @Test
  void blankHeaderNameThrows() {
    Map<String, String> headers = new LinkedHashMap<>();
    headers.put("", "value");
    assertThatThrownBy(() -> new RegistrationIdempotencyResponseSnapshot("SUCCESS", "body", headers))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void blockedHeaderNameThrows() {
    assertThatThrownBy(
            () ->
                new RegistrationIdempotencyResponseSnapshot(
                    "SUCCESS", "body", Map.of("Cookie", "a=b")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void headerValueContainingAuthFlowSidThrows() {
    assertThatThrownBy(
            () ->
                new RegistrationIdempotencyResponseSnapshot(
                    "SUCCESS", "body", Map.of("X-Custom", "auth_flow_sid=abc")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void validSafeHeadersAreRetained() {
    RegistrationIdempotencyResponseSnapshot snapshot =
        new RegistrationIdempotencyResponseSnapshot("SUCCESS", "body", Map.of("Retry-After", "30"));
    assertThat(snapshot.safeHeaders()).containsEntry("Retry-After", "30");
  }

  @Test
  void toStringDoesNotLeakSerializedBody() {
    RegistrationIdempotencyResponseSnapshot snapshot =
        new RegistrationIdempotencyResponseSnapshot("SUCCESS", "sensitive-body-content", Map.of());
    assertThat(snapshot.toString()).doesNotContain("sensitive-body-content");
    assertThat(snapshot.toString()).contains("SUCCESS");
  }
}
