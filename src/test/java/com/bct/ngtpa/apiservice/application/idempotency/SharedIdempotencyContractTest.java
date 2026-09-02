package com.bct.ngtpa.apiservice.application.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SharedIdempotencyContractTest {
  private static final Instant NOW = Instant.parse("2026-09-02T00:00:00Z");

  @Test
  void completedRecordCarriesSafeTerminalResponseRegardlessOfPublicSuccess() {
    var snapshot = snapshot(422, "{\"success\":false,\"status\":\"BUSINESS_REJECTED\"}", null);
    var record =
        new IdempotencyRecord(
            operation(), fingerprint(), IdempotencyStatus.COMPLETED, snapshot, NOW, NOW,
            NOW.plusSeconds(300));

    assertThat(record.terminalResponse()).isEqualTo(snapshot);
    assertThat(snapshot.httpStatus()).isEqualTo(422);
    assertThat(snapshot.serializedSafeBody()).contains("BUSINESS_REJECTED");
  }

  @Test
  void protectedReferenceIsTypedValidatedAndRedacted() {
    var reference = new ProtectedReplayReference("protectedReplay01");
    assertThat(reference.toString()).doesNotContain("protectedReplay01").contains("<redacted>");
    assertThatThrownBy(() -> new ProtectedReplayReference("short"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsSensitiveOrUnapprovedHeaders() {
    assertThatThrownBy(() -> snapshot(200, "{}", Map.of("Set-Cookie", "secret")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> snapshot(200, "{}", Map.of("Authorization", "secret")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> snapshot(200, "{}", Map.of("X-XSRF-TOKEN", "secret")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void operationIdentityIsContextNeutralAndRedacted() {
    assertThat(operation().toString())
        .contains("capability=profile", "action=update-details", "scope=<redacted>", "key=<redacted>")
        .doesNotContain("opaqueScope01", "requestKey01");
  }

  @Test
  void capturedResponseRequiresMatchingTransportSemantics() {
    var response =
        new HydratedIdempotentResponse(
            200, "application/json", "{\"success\":true}", Map.of("Content-Language", "en"));
    var snapshot =
        new IdempotentTerminalResponseSnapshot(
            200 / 200,
            200,
            "application/json",
            "{\"success\":true}",
            Map.of("Content-Language", "en"),
            null);

    assertThat(new IdempotentCapturedResponse(response, snapshot).response()).isEqualTo(response);
    assertThatThrownBy(
            () ->
                new IdempotentCapturedResponse(
                    response,
                    new IdempotentTerminalResponseSnapshot(
                        1, 422, "application/json", "{}", Map.of("Content-Language", "en"), null)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void nonCompletedRecordCannotCarryTerminalResponse() {
    assertThatThrownBy(
            () -> new IdempotencyRecord(
                operation(), fingerprint(), IdempotencyStatus.PROCESSING,
                snapshot(200, "{}", null), NOW, NOW, NOW.plusSeconds(300)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static IdempotentTerminalResponseSnapshot snapshot(
      int status, String body, Map<String, String> headers) {
    return new IdempotentTerminalResponseSnapshot(1, status, "application/json", body, headers, null);
  }

  private static IdempotencyOperationId operation() {
    return new IdempotencyOperationId(
        new IdempotencyCapability("profile"), new IdempotencyAction("update-details"),
        new IdempotencyOperationScope("opaqueScope01"), new IdempotencyKey("requestKey01"));
  }

  private static IdempotencyFingerprint fingerprint() {
    return new IdempotencyFingerprint(1, "a".repeat(64));
  }
}
