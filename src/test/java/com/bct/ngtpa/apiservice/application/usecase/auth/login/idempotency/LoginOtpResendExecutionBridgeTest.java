package com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.idempotency.HydratedIdempotentResponse;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.resend.ResendLoginOtpResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.resend.ResendLoginOtpStatus;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class LoginOtpResendExecutionBridgeTest {
  private final LoginOtpResendExecutionBridge bridge =
      new LoginOtpResendExecutionBridge(
          testJson(), new LoginOtpResendReplayCodec(new LoginResponseEnvelope(testJson())));

  @Test
  void acquiredAndReplayResponsesRestoreOriginalCooldownResult() {
    var original =
        new ResendLoginOtpResult(ResendLoginOtpStatus.COOLDOWN_ACTIVE, "****4567", null, 60, 25);
    var transientResponse = bridge.toTransientResponse(original);
    assertThat(transientResponse.safeHeaders()).isEqualTo(Map.of("Retry-After", "25"));
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
  void rejectsMalformedUnknownFieldAndHeaderMismatch() {
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
                        "{\"status\":\"RESENT\",\"maskedDestination\":\"****4567\",\"expiresInSeconds\":300,\"resendCooldownSeconds\":60,\"retryAfterSeconds\":null,\"rawOtp\":\"123456\"}",
                        Map.of())))
        .isInstanceOf(IllegalArgumentException.class);
    var cooldown =
        bridge.toTransientResponse(
            new ResendLoginOtpResult(
                ResendLoginOtpStatus.COOLDOWN_ACTIVE, "****4567", null, 60, 25));
    assertThatThrownBy(
            () ->
                bridge.fromTransientResponse(
                    new HydratedIdempotentResponse(
                        429,
                        "application/json",
                        cooldown.serializedBody(),
                        Map.of("Retry-After", "24"))))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static com.bct.ngtpa.apiservice.application.port.out.TransientJsonPort testJson() {
    return new com.bct.ngtpa.apiservice.adapter.out.json.JacksonTransientJsonAdapter(
        new com.fasterxml.jackson.databind.ObjectMapper());
  }
}
