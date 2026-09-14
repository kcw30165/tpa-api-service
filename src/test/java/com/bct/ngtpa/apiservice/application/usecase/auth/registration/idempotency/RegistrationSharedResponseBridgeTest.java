package com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.idempotency.IdempotentTerminalResponseSnapshot;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RegistrationSharedResponseBridgeTest {
  private final RegistrationSharedResponseEnvelope envelope =
      new RegistrationSharedResponseEnvelope();
  private final RegistrationSharedResponseBridge bridge = new RegistrationSharedResponseBridge();

  @Test
  void capturesAndHydratesEveryActionWithoutInvokingProtectedOperation() {
    AtomicInteger protectedCalls = new AtomicInteger();
    for (RegistrationSharedIdempotencyAction action :
        RegistrationSharedIdempotencyAction.values()) {
      String body = envelope.encode(action, Map.of("status", "BUSINESS_REJECTED"));
      var terminal =
          new RegistrationSharedTerminalResponse(
              action, 422, "application/json", body, Map.of("Content-Language", "en"), null);
      var captured = bridge.capture(terminal);
      var hydrated = bridge.hydrate(action, captured.snapshot());
      assertThat(hydrated).isEqualTo(captured.response());
      assertThat(hydrated.serializedBody()).isEqualTo(body);
    }
    assertThat(protectedCalls).hasValue(0);
  }

  @Test
  void preservesOnlySharedSafeHeadersAndOpaqueProtectedReference() {
    String body =
        envelope.encode(RegistrationSharedIdempotencyAction.START, Map.of("status", "SUCCESS"));
    var captured =
        bridge.capture(
            new RegistrationSharedTerminalResponse(
                RegistrationSharedIdempotencyAction.START,
                200,
                "application/json",
                body,
                Map.of("Content-Language", "en"),
                "protectedReplayReference01"));
    assertThat(captured.snapshot().safeHeaders()).containsOnlyKeys("Content-Language");
    assertThat(captured.snapshot().protectedReplayReference().value())
        .isEqualTo("protectedReplayReference01");
    assertThat(captured.snapshot().toString()).doesNotContain(body, "protectedReplayReference01");
  }

  @Test
  void rejectsUnsafeHeadersUnknownSchemaAndActionMismatch() {
    String start =
        envelope.encode(RegistrationSharedIdempotencyAction.START, Map.of("status", "SUCCESS"));
    assertThatThrownBy(
            () ->
                bridge.capture(
                    new RegistrationSharedTerminalResponse(
                        RegistrationSharedIdempotencyAction.START,
                        200,
                        "application/json",
                        start,
                        Map.of("Set-Cookie", "auth_flow_sid=secret"),
                        null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageNotContaining("secret");

    assertThatThrownBy(
            () ->
                new IdempotentTerminalResponseSnapshot(
                    2, 200, "application/json", start, Map.of(), null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("unsupported idempotent response snapshot schema");

    var correctVersion =
        new IdempotentTerminalResponseSnapshot(1, 200, "application/json", start, Map.of(), null);
    assertThatThrownBy(
            () -> bridge.hydrate(RegistrationSharedIdempotencyAction.OTP_VERIFY, correctVersion))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsMalformedNestedMarkerUnknownActionAndUnknownEnvelopeFields() {
    var nestedMarker =
        new IdempotentTerminalResponseSnapshot(
            1,
            200,
            "application/json",
            "{\"idempotencyAction\":\"complete\",\"payload\":{\"text\":\"\\\"idempotencyAction\\\":\\\"start\\\"\"}}",
            Map.of(),
            null);
    assertThatThrownBy(
            () -> bridge.hydrate(RegistrationSharedIdempotencyAction.START, nestedMarker))
        .isInstanceOf(IllegalArgumentException.class);

    var unknownAction =
        new IdempotentTerminalResponseSnapshot(
            1,
            200,
            "application/json",
            "{\"idempotencyAction\":\"unknown\",\"payload\":{}}",
            Map.of(),
            null);
    assertThatThrownBy(
            () -> bridge.hydrate(RegistrationSharedIdempotencyAction.START, unknownAction))
        .isInstanceOf(IllegalArgumentException.class);

    var unknownField =
        new IdempotentTerminalResponseSnapshot(
            1,
            200,
            "application/json",
            "{\"idempotencyAction\":\"start\",\"payload\":{},\"unexpected\":true}",
            Map.of(),
            null);
    assertThatThrownBy(
            () -> bridge.hydrate(RegistrationSharedIdempotencyAction.START, unknownField))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void terminalDiagnosticRenderingRedactsBodyAndProtectedReference() {
    String body =
        envelope.encode(
            RegistrationSharedIdempotencyAction.COMPLETE,
            Map.of("password", "must-never-be-present"));
    var terminal =
        new RegistrationSharedTerminalResponse(
            RegistrationSharedIdempotencyAction.COMPLETE,
            200,
            "application/json",
            body,
            Map.of(),
            "protectedReplayReference01");
    assertThat(terminal.toString())
        .doesNotContain("must-never-be-present", "protectedReplayReference01");
  }
}
