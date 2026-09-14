package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.otpverify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.idempotency.ActionTaggedIdempotencyResponseEnvelope;
import com.bct.ngtpa.apiservice.application.idempotency.HydratedIdempotentResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentTerminalResponseSnapshot;
import com.bct.ngtpa.apiservice.application.port.out.TransientJsonPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.verify.VerifyForgotPasswordOtpResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.verify.VerifyForgotPasswordOtpStatus;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class ForgotPasswordOtpVerifyReplayCodecTest {
  private final Json json = new Json();
  private final ForgotPasswordOtpVerifyReplayCodec codec =
      new ForgotPasswordOtpVerifyReplayCodec(new ActionTaggedIdempotencyResponseEnvelope(json));
  private final ForgotPasswordOtpVerifyExecutionBridge bridge =
      new ForgotPasswordOtpVerifyExecutionBridge(json, codec);

  @Test
  void roundTripsOnlyPublicOtpVerifyResultFields() {
    var result =
        new VerifyForgotPasswordOtpResult(
            VerifyForgotPasswordOtpStatus.OTP_INVALID, WorkflowNextStep.VERIFY_OTP, 3);
    var transientResponse = bridge.toTransientResponse(result);
    StepVerifier.create(
            codec.capture(result, transientResponse).flatMap(c -> codec.hydrate(c.snapshot())))
        .expectNext(result)
        .verifyComplete();
    assertThat(transientResponse.serializedBody())
        .contains("OTP_INVALID", "VERIFY_OTP", "3")
        .doesNotContain("123456", "csrf", "authorizationId", "resetSubjectId");
  }

  @Test
  void rejectsActionMismatchUnknownFieldsAndProtectedReference() {
    String mismatch =
        "{\"idempotencyAction\":\"otp-resend\",\"payload\":{\"status\":\"OTP_INVALID\",\"nextStep\":\"VERIFY_OTP\",\"remainingVerifyAttempts\":3}}";
    var mismatchSnapshot =
        new IdempotentTerminalResponseSnapshot(
            1, 400, "application/json", mismatch, Map.of(), null);
    StepVerifier.create(codec.hydrate(mismatchSnapshot))
        .expectError(IllegalArgumentException.class)
        .verify();

    String unknown =
        "{\"idempotencyAction\":\"otp-verify\",\"payload\":{\"status\":\"OTP_INVALID\",\"nextStep\":\"VERIFY_OTP\",\"remainingVerifyAttempts\":3,\"code\":\"123456\"}}";
    var unknownSnapshot =
        new IdempotentTerminalResponseSnapshot(1, 400, "application/json", unknown, Map.of(), null);
    StepVerifier.create(codec.hydrate(unknownSnapshot))
        .expectError(IllegalArgumentException.class)
        .verify();

    assertThatThrownBy(
            () ->
                bridge.fromTransientResponse(
                    new HydratedIdempotentResponse(
                            400, "application/json", json.write(new Object()), Map.of())
                        .withProtectedReplayReference(
                            new com.bct.ngtpa.apiservice.application.idempotency
                                .ProtectedReplayReference("0123456789abcdef0123456789abcdef"))))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static final class Json implements TransientJsonPort {
    private final ObjectMapper mapper =
        new ObjectMapper().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    public String write(Object value) {
      try {
        return mapper.writeValueAsString(value);
      } catch (Exception e) {
        throw new IllegalArgumentException(e);
      }
    }

    public <T> T read(String value, Class<T> type) {
      try {
        return mapper.readValue(value, type);
      } catch (Exception e) {
        throw new IllegalArgumentException(e);
      }
    }
  }
}
