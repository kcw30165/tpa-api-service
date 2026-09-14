package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.complete;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.idempotency.ActionTaggedIdempotencyResponseEnvelope;
import com.bct.ngtpa.apiservice.application.idempotency.HydratedIdempotentResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentTerminalResponseSnapshot;
import com.bct.ngtpa.apiservice.application.idempotency.ProtectedReplayReference;
import com.bct.ngtpa.apiservice.application.port.out.TransientJsonPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.complete.CompleteForgotPasswordResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.complete.CompleteForgotPasswordStatus;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class ForgotPasswordCompleteReplayCodecTest {
  private final Json json = new Json();
  private final ForgotPasswordCompleteReplayCodec codec =
      new ForgotPasswordCompleteReplayCodec(new ActionTaggedIdempotencyResponseEnvelope(json));
  private final ForgotPasswordCompleteExecutionBridge bridge =
      new ForgotPasswordCompleteExecutionBridge(json, codec);

  @Test
  void roundTripsSuccessAndBothCookieCleanupFlagsWithoutSensitiveMaterial() {
    var result =
        new CompleteForgotPasswordResult(
            CompleteForgotPasswordStatus.SUCCESS,
            WorkflowNextStep.RETURN_TO_LOGIN,
            true,
            true,
            List.of());
    var response = bridge.toTransientResponse(result);

    StepVerifier.create(
            codec.capture(result, response).flatMap(captured -> codec.hydrate(captured.snapshot())))
        .expectNext(result)
        .verifyComplete();

    assertThat(response.serializedBody())
        .contains("SUCCESS", "RETURN_TO_LOGIN")
        .doesNotContain(
            "password",
            "confirmPassword",
            "csrfToken",
            "authFlowSessionId",
            "authorizationId",
            "resetSubjectId");
  }

  @Test
  void roundTripsRecognizedBusinessRejectionAsTerminalPublicResult() {
    var result =
        new CompleteForgotPasswordResult(
            CompleteForgotPasswordStatus.BUSINESS_REJECTED,
            WorkflowNextStep.PASSWORD_SETUP,
            false,
            false,
            List.of());

    StepVerifier.create(
            codec
                .capture(result, bridge.toTransientResponse(result))
                .flatMap(captured -> bridge.hydrate(captured.snapshot()))
                .map(bridge::fromTransientResponse))
        .expectNext(result)
        .verifyComplete();
  }

  @Test
  void rejectsUnknownOutcomeActionMismatchUnknownFieldsAndProtectedReference() {
    var unknown =
        new CompleteForgotPasswordResult(
            CompleteForgotPasswordStatus.UNKNOWN_OUTCOME,
            WorkflowNextStep.PASSWORD_SETUP,
            false,
            false,
            List.of());
    assertThatThrownBy(() -> bridge.toTransientResponse(unknown))
        .isInstanceOf(IllegalArgumentException.class);

    String mismatch =
        "{\"idempotencyAction\":\"otp-verify\",\"payload\":{\"status\":\"SUCCESS\",\"nextStep\":\"RETURN_TO_LOGIN\",\"clearAuthFlowCookie\":true,\"clearXsrfCookie\":true,\"errors\":[]}}";
    StepVerifier.create(
            codec.hydrate(
                new IdempotentTerminalResponseSnapshot(
                    1, 200, "application/json", mismatch, Map.of(), null)))
        .expectError(IllegalArgumentException.class)
        .verify();

    String unknownField =
        "{\"idempotencyAction\":\"complete\",\"payload\":{\"status\":\"SUCCESS\",\"nextStep\":\"RETURN_TO_LOGIN\",\"clearAuthFlowCookie\":true,\"clearXsrfCookie\":true,\"errors\":[],\"password\":\"secret\"}}";
    StepVerifier.create(
            codec.hydrate(
                new IdempotentTerminalResponseSnapshot(
                    1, 200, "application/json", unknownField, Map.of(), null)))
        .expectError(IllegalArgumentException.class)
        .verify();

    assertThatThrownBy(
            () ->
                bridge.fromTransientResponse(
                    new HydratedIdempotentResponse(
                            200, "application/json", json.write(new Object()), Map.of())
                        .withProtectedReplayReference(
                            new ProtectedReplayReference("0123456789abcdef0123456789abcdef"))))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static final class Json implements TransientJsonPort {
    private final ObjectMapper mapper =
        new ObjectMapper().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    @Override
    public String write(Object value) {
      try {
        return mapper.writeValueAsString(value);
      } catch (Exception exception) {
        throw new IllegalArgumentException(exception);
      }
    }

    @Override
    public <T> T read(String value, Class<T> type) {
      try {
        return mapper.readValue(value, type);
      } catch (Exception exception) {
        throw new IllegalArgumentException(exception);
      }
    }
  }
}
