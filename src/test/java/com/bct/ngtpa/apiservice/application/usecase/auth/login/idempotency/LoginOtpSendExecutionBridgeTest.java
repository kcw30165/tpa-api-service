package com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.idempotency.HydratedIdempotentResponse;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.SendLoginOtpResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.SendLoginOtpStatus;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class LoginOtpSendExecutionBridgeTest {
  private final LoginOtpSendExecutionBridge bridge =
      new LoginOtpSendExecutionBridge(
          testJson(), new LoginOtpSendReplayCodec(new LoginResponseEnvelope(testJson())));

  @Test
  void transientCaptureAndReplayRestoreOriginalSentResult() {
    var original = new SendLoginOtpResult(SendLoginOtpStatus.SENT, "****4567", 300, 60, null);
    var transientResponse = bridge.toTransientResponse(original);
    assertThat(bridge.fromTransientResponse(transientResponse)).isEqualTo(original);

    StepVerifier.create(bridge.capture(transientResponse))
        .assertNext(
            captured -> {
              assertThat(captured.response()).isEqualTo(transientResponse);
              StepVerifier.create(bridge.hydrate(captured.snapshot()))
                  .assertNext(
                      hydrated ->
                          assertThat(bridge.fromTransientResponse(hydrated)).isEqualTo(original))
                  .verifyComplete();
            })
        .verifyComplete();
  }

  @Test
  void rejectsMalformedUnknownFieldAndTransportMismatch() {
    assertThatThrownBy(
            () ->
                bridge.fromTransientResponse(
                    new HydratedIdempotentResponse(200, "application/json", "{}", Map.of())))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                bridge.fromTransientResponse(
                    new HydratedIdempotentResponse(
                        200,
                        "application/json",
                        "{\"status\":\"SENT\",\"maskedDestination\":\"****4567\",\"expiresInSeconds\":300,\"resendCooldownSeconds\":60,\"retryAfterSeconds\":null,\"rawOtp\":\"123456\"}",
                        Map.of())))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                bridge.fromTransientResponse(
                    new HydratedIdempotentResponse(
                        401,
                        "application/json",
                        bridge
                            .toTransientResponse(
                                new SendLoginOtpResult(
                                    SendLoginOtpStatus.SENT, "****4567", 300, 60, null))
                            .serializedBody(),
                        Map.of())))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static com.bct.ngtpa.apiservice.application.port.out.TransientJsonPort testJson() {
    return new com.bct.ngtpa.apiservice.adapter.out.json.JacksonTransientJsonAdapter(
        new com.fasterxml.jackson.databind.ObjectMapper());
  }
}
