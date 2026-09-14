package com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class LoginResponseEnvelopeTest {
  private final LoginResponseEnvelope envelope = new LoginResponseEnvelope(testJson());

  @Test
  void roundTripsEachStableActionWithTypedPayload() {
    for (LoginIdempotencyAction action : LoginIdempotencyAction.values()) {
      String json = envelope.encode(action, new TestPayload("ORIGINAL_STATUS", 25));

      assertThat(envelope.decodeAction(json)).isEqualTo(action);
      assertThat(envelope.decodePayload(action, json, TestPayload.class))
          .isEqualTo(new TestPayload("ORIGINAL_STATUS", 25));
    }
  }

  @Test
  void rejectsMalformedBlankAndMissingPayloadBodies() {
    assertInvalid(null);
    assertInvalid(" ");
    assertInvalid("not-json");
    assertInvalid("{\"idempotencyAction\":\"credentials\"}");
    assertInvalid("{\"idempotencyAction\":\"credentials\",\"payload\":null}");
  }

  @Test
  void rejectsUnsupportedActionAndNestedFalseMarker() {
    assertInvalid("{\"idempotencyAction\":\"unknown\",\"payload\":{}}");
    assertThatThrownBy(
            () ->
                envelope.decodePayload(
                    LoginIdempotencyAction.CREDENTIALS,
                    "{\"idempotencyAction\":\"otp-send\",\"payload\":{\"status\":\"credentials\",\"retryAfterSeconds\":null}}",
                    TestPayload.class))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Login replay action mismatch");
  }

  @Test
  void rejectsUnknownTopLevelAndTypedPayloadFields() {
    assertInvalid("{\"idempotencyAction\":\"credentials\",\"payload\":{},\"unexpected\":true}");
    assertThatThrownBy(
            () ->
                envelope.decodePayload(
                    LoginIdempotencyAction.CREDENTIALS,
                    "{\"idempotencyAction\":\"credentials\",\"payload\":{\"status\":\"ORIGINAL_STATUS\",\"retryAfterSeconds\":25,\"secret\":\"forbidden\"}}",
                    TestPayload.class))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Login replay response decoding failed");
  }

  @Test
  void rejectsWrongExpectedActionWithoutInferringFromPayload() {
    String json =
        envelope.encode(LoginIdempotencyAction.OTP_RESEND, new TestPayload("credentials", null));

    assertThatThrownBy(
            () ->
                envelope.decodePayload(LoginIdempotencyAction.CREDENTIALS, json, TestPayload.class))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Login replay action mismatch");
  }

  @Test
  void diagnosticsDoNotExposeSerializedBody() {
    String protectedValue = "csrf-session-password-otp";

    assertThatThrownBy(() -> envelope.decodeAction(protectedValue))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Login replay response decoding failed")
        .hasMessageNotContaining(protectedValue);
  }

  private void assertInvalid(String value) {
    assertThatThrownBy(() -> envelope.decodeAction(value))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private record TestPayload(String status, Integer retryAfterSeconds) {}

  private static com.bct.ngtpa.apiservice.application.port.out.TransientJsonPort testJson() {
    return new com.bct.ngtpa.apiservice.adapter.out.json.JacksonTransientJsonAdapter(
        new com.fasterxml.jackson.databind.ObjectMapper());
  }
}
