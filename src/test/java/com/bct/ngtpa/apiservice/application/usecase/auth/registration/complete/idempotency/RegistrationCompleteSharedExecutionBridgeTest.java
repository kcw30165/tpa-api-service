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
}
