package com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationLookupAccount;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.idempotency.HydratedIdempotentResponse;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete.CompleteRegistrationResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete.CompleteRegistrationResultStatus;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RegistrationCompleteSharedExecutionBridgeTest {
  private final RegistrationCompleteSharedExecutionBridge bridge =
      new RegistrationCompleteSharedExecutionBridge(new RegistrationCompleteReplayCodec());

  @Test
  void decodesCapturedTerminalEnvelopeWithRemainingMemberAccount() {
    CompleteRegistrationResult original =
        new CompleteRegistrationResult(
            CompleteRegistrationResultStatus.REGISTERED_WITH_REMAINING_TRUSTS,
            WorkflowNextStep.SELECT_TRUST,
            true,
            false,
            List.of(
                new RegistrationLookupAccount(
                    "TB", "MSIG", "OE", "00000004203", "2", "", "", false, "")),
            List.of());

    var captured = bridge.capture(bridge.toTransientResponse(original)).block();
    HydratedIdempotentResponse executorResponse =
        new HydratedIdempotentResponse(
            captured.snapshot().httpStatus(),
            captured.snapshot().mediaType(),
            captured.snapshot().serializedSafeBody(),
            captured.snapshot().safeHeaders());

    CompleteRegistrationResult decoded = bridge.fromTransientResponse(executorResponse);

    assertThat(decoded.status())
        .isEqualTo(CompleteRegistrationResultStatus.REGISTERED_WITH_REMAINING_TRUSTS);
    assertThat(decoded.nextStep()).isEqualTo(WorkflowNextStep.SELECT_TRUST);
    assertThat(decoded.registered()).isTrue();
    assertThat(decoded.remainingTrusts()).containsExactly(original.remainingTrusts().getFirst());
  }

  @Test
  void stillDecodesDirectTransientResponseAndRejectsWrongEnvelopeAction() {
    CompleteRegistrationResult original =
        new CompleteRegistrationResult(
            CompleteRegistrationResultStatus.REGISTERED,
            WorkflowNextStep.RETURN_TO_LOGIN,
            true,
            true,
            List.of(),
            List.of());
    assertThat(bridge.fromTransientResponse(bridge.toTransientResponse(original)))
        .isEqualTo(original);
    assertThatThrownBy(
            () ->
                bridge.fromTransientResponse(
                    new HydratedIdempotentResponse(
                        200,
                        "application/json",
                        "{\"idempotencyAction\":\"start\",\"payload\":{}}",
                        Map.of())))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Registration Complete transient response decoding failed");
  }

  // ----- toTransientResponse(): lifecycle-only-status guard (added round 225) -----

  @Test
  void toTransientResponseRejectsCompletedReplayLifecycleStatus() {
    CompleteRegistrationResult lifecycleOnly =
        new CompleteRegistrationResult(
            CompleteRegistrationResultStatus.COMPLETED_REPLAY,
            WorkflowNextStep.RETURN_TO_LOGIN,
            true,
            true,
            List.of(),
            List.of());
    assertThatThrownBy(() -> bridge.toTransientResponse(lifecycleOnly))
        .isInstanceOf(IllegalArgumentException.class);
  }

  // ----- fromTransientResponse(): guards not yet exercised (added round 225) -------

  @Test
  void fromTransientResponseRejectsNullResponse() {
    assertThatThrownBy(() -> bridge.fromTransientResponse(null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void fromTransientResponseRejectsWrongMediaType() {
    HydratedIdempotentResponse response =
        new HydratedIdempotentResponse(200, "text/plain", "{}", Map.of());
    assertThatThrownBy(() -> bridge.fromTransientResponse(response))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void fromTransientResponseRejectsNonObjectBody() {
    HydratedIdempotentResponse response =
        new HydratedIdempotentResponse(200, "application/json", "[1,2,3]", Map.of());
    assertThatThrownBy(() -> bridge.fromTransientResponse(response))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void fromTransientResponseRejectsActionWithoutPayload() {
    HydratedIdempotentResponse response =
        new HydratedIdempotentResponse(
            200, "application/json", "{\"idempotencyAction\":\"complete\"}", Map.of());
    assertThatThrownBy(() -> bridge.fromTransientResponse(response))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void fromTransientResponseRejectsNonObjectPayload() {
    HydratedIdempotentResponse response =
        new HydratedIdempotentResponse(
            200,
            "application/json",
            "{\"idempotencyAction\":\"complete\",\"payload\":[1,2,3]}",
            Map.of());
    assertThatThrownBy(() -> bridge.fromTransientResponse(response))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void fromTransientResponseRejectsMalformedJson() {
    HydratedIdempotentResponse response =
        new HydratedIdempotentResponse(200, "application/json", "{not valid json", Map.of());
    assertThatThrownBy(() -> bridge.fromTransientResponse(response))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void fromTransientResponseRejectsHttpStatusMismatch() {
    CompleteRegistrationResult original =
        new CompleteRegistrationResult(
            CompleteRegistrationResultStatus.REGISTERED,
            WorkflowNextStep.RETURN_TO_LOGIN,
            true,
            true,
            List.of(),
            List.of());
    HydratedIdempotentResponse valid = bridge.toTransientResponse(original);
    HydratedIdempotentResponse mismatched =
        new HydratedIdempotentResponse(404, valid.mediaType(), valid.serializedBody(), Map.of());
    assertThatThrownBy(() -> bridge.fromTransientResponse(mismatched))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void fromTransientResponseRejectsNullRequiredWireField() {
    HydratedIdempotentResponse response =
        new HydratedIdempotentResponse(
            200,
            "application/json",
            "{\"idempotencyAction\":\"complete\",\"payload\":{\"status\":null,\"nextStep\":\"RETURN_TO_LOGIN\",\"registered\":true,\"clearAuthFlowCookie\":true,\"remainingTrusts\":[],\"errors\":[]}}",
            Map.of());
    assertThatThrownBy(() -> bridge.fromTransientResponse(response))
        .isInstanceOf(IllegalArgumentException.class);
  }

  // ----- hydrate(): entirely untested prior to this round (added round 225) ---------

  @Test
  void hydrateDelegatesToCodecAndReturnsCorrectHttpStatus() {
    CompleteRegistrationResult original =
        new CompleteRegistrationResult(
            CompleteRegistrationResultStatus.REGISTERED,
            WorkflowNextStep.RETURN_TO_LOGIN,
            true,
            true,
            List.of(),
            List.of());
    var captured = bridge.capture(bridge.toTransientResponse(original)).block();
    HydratedIdempotentResponse hydrated = bridge.hydrate(captured.snapshot()).block();
    assertThat(hydrated.httpStatus()).isEqualTo(200);
    assertThat(hydrated.mediaType()).isEqualTo("application/json");
  }

  // ----- httpStatus(): remaining switch buckets (added round 225 - only
  // REGISTERED/REGISTERED_WITH_REMAINING_TRUSTS (200) were previously tested) --------

  @Test
  void toTransientResponseValidationFailedYields400() {
    CompleteRegistrationResult result =
        new CompleteRegistrationResult(
            CompleteRegistrationResultStatus.VALIDATION_FAILED,
            WorkflowNextStep.RETURN_TO_LOGIN,
            false,
            false,
            List.of(),
            List.of());
    assertThat(bridge.toTransientResponse(result).httpStatus()).isEqualTo(400);
  }

  @Test
  void toTransientResponseIdempotencyKeyReusedYields409() {
    CompleteRegistrationResult result =
        new CompleteRegistrationResult(
            CompleteRegistrationResultStatus.IDEMPOTENCY_KEY_REUSED,
            WorkflowNextStep.RETURN_TO_LOGIN,
            false,
            false,
            List.of(),
            List.of());
    assertThat(bridge.toTransientResponse(result).httpStatus()).isEqualTo(409);
  }

  @Test
  void toTransientResponseDownstreamRejectedYields422() {
    CompleteRegistrationResult result =
        new CompleteRegistrationResult(
            CompleteRegistrationResultStatus.DOWNSTREAM_REJECTED,
            WorkflowNextStep.RETURN_TO_LOGIN,
            false,
            false,
            List.of(),
            List.of());
    assertThat(bridge.toTransientResponse(result).httpStatus()).isEqualTo(422);
  }

  @Test
  void toTransientResponseSystemFailedYields500() {
    CompleteRegistrationResult result =
        new CompleteRegistrationResult(
            CompleteRegistrationResultStatus.SYSTEM_FAILED,
            WorkflowNextStep.RETURN_TO_LOGIN,
            false,
            false,
            List.of(),
            List.of());
    assertThat(bridge.toTransientResponse(result).httpStatus()).isEqualTo(500);
  }
}
