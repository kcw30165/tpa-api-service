package com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationLookupAccount;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorDescriptor;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorSeverity;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorType;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentCapturedResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentTerminalResponseSnapshot;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete.CompleteRegistrationResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete.CompleteRegistrationResultStatus;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Branch-oriented tests for {@link RegistrationCompleteReplayCodec}. Both internal collaborators
 * ({@code RegistrationSharedResponseEnvelope}/{@code RegistrationSharedResponseBridge}) are
 * constructed internally (round 102 verbatim confirmation) - exercised for real, since they cannot
 * be substituted.
 */
class RegistrationCompleteReplayCodecTest {

  private final RegistrationCompleteReplayCodec codec = new RegistrationCompleteReplayCodec();

  private static CompleteRegistrationResult result(
      CompleteRegistrationResultStatus status,
      WorkflowNextStep nextStep,
      boolean registered,
      boolean clearAuthFlowCookie,
      List<RegistrationLookupAccount> remainingTrusts,
      List<WorkflowErrorDescriptor> errors) {
    return new CompleteRegistrationResult(
        status, nextStep, registered, clearAuthFlowCookie, remainingTrusts, errors);
  }

  private static RegistrationLookupAccount trust() {
    return new RegistrationLookupAccount(
        "env1", "trust1", "SCHEME_A", "policy1", "cert1", "ACTIVE", "2026-01-01", true, "cust1");
  }

  private static WorkflowErrorDescriptor error() {
    return new WorkflowErrorDescriptor(
        WorkflowErrorType.FIELD,
        "code1",
        "message1",
        List.of("field1"),
        WorkflowErrorSeverity.ERROR,
        "SERVER");
  }

  // ----- capture(): guards + lifecycle-only rejection --------------------------------

  @Test
  void captureNullResultThrows() {
    assertThatThrownBy(() -> codec.capture(null)).isInstanceOf(NullPointerException.class);
  }

  @Test
  void captureCompletedReplayStatusThrows() {
    CompleteRegistrationResult lifecycleOnly =
        result(
            CompleteRegistrationResultStatus.COMPLETED_REPLAY,
            WorkflowNextStep.RETURN_TO_LOGIN,
            true,
            true,
            List.of(),
            List.of());
    assertThatThrownBy(() -> codec.capture(lifecycleOnly))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void captureUnknownOutcomeStatusThrows() {
    CompleteRegistrationResult lifecycleOnly =
        result(
            CompleteRegistrationResultStatus.UNKNOWN_OUTCOME,
            WorkflowNextStep.RETURN_TO_LOGIN,
            true,
            true,
            List.of(),
            List.of());
    assertThatThrownBy(() -> codec.capture(lifecycleOnly))
        .isInstanceOf(IllegalArgumentException.class);
  }

  // ----- capture(): success (with trusts/errors) -> hydrate() round-trip -------------

  @Test
  void captureAndHydrateRoundTripsRegisteredWithTrustsAndErrors() {
    CompleteRegistrationResult original =
        result(
            CompleteRegistrationResultStatus.REGISTERED_WITH_REMAINING_TRUSTS,
            WorkflowNextStep.RETURN_TO_LOGIN,
            true,
            false,
            List.of(trust()),
            List.of(error()));
    IdempotentCapturedResponse captured = codec.capture(original);
    assertThat(captured.response().httpStatus()).isEqualTo(200);

    CompleteRegistrationResult rehydrated = codec.hydrate(captured.snapshot());
    assertThat(rehydrated.status())
        .isEqualTo(CompleteRegistrationResultStatus.REGISTERED_WITH_REMAINING_TRUSTS);
    assertThat(rehydrated.nextStep()).isEqualTo(WorkflowNextStep.RETURN_TO_LOGIN);
    assertThat(rehydrated.registered()).isTrue();
    assertThat(rehydrated.clearAuthFlowCookie()).isFalse();
    assertThat(rehydrated.remainingTrusts()).hasSize(1);
    assertThat(rehydrated.remainingTrusts().get(0).trustCode()).isEqualTo("trust1");
    assertThat(rehydrated.errors()).hasSize(1);
    assertThat(rehydrated.errors().get(0).code()).isEqualTo("code1");
  }

  @Test
  void captureAndHydrateRoundTripsSimpleRegisteredWithNoTrustsOrErrors() {
    CompleteRegistrationResult original =
        result(
            CompleteRegistrationResultStatus.REGISTERED,
            WorkflowNextStep.RETURN_TO_LOGIN,
            true,
            true,
            List.of(),
            List.of());
    IdempotentCapturedResponse captured = codec.capture(original);
    CompleteRegistrationResult rehydrated = codec.hydrate(captured.snapshot());
    assertThat(rehydrated.status()).isEqualTo(CompleteRegistrationResultStatus.REGISTERED);
    assertThat(rehydrated.remainingTrusts()).isEmpty();
    assertThat(rehydrated.errors()).isEmpty();
  }

  // ----- capture(): each httpStatus() switch bucket ---------------------------------

  @Test
  void captureValidationFailedYields400() {
    CompleteRegistrationResult r =
        result(
            CompleteRegistrationResultStatus.VALIDATION_FAILED,
            WorkflowNextStep.SELECT_OTP_DESTINATION,
            false,
            false,
            List.of(),
            List.of(error()));
    IdempotentCapturedResponse captured = codec.capture(r);
    assertThat(captured.response().httpStatus()).isEqualTo(400);
  }

  @Test
  void captureIdempotencyKeyReusedYields409() {
    CompleteRegistrationResult r =
        result(
            CompleteRegistrationResultStatus.IDEMPOTENCY_KEY_REUSED,
            WorkflowNextStep.SELECT_OTP_DESTINATION,
            false,
            false,
            List.of(),
            List.of());
    IdempotentCapturedResponse captured = codec.capture(r);
    assertThat(captured.response().httpStatus()).isEqualTo(409);
  }

  @Test
  void captureDownstreamRejectedYields422() {
    CompleteRegistrationResult r =
        result(
            CompleteRegistrationResultStatus.DOWNSTREAM_REJECTED,
            WorkflowNextStep.SELECT_OTP_DESTINATION,
            false,
            false,
            List.of(),
            List.of(error()));
    IdempotentCapturedResponse captured = codec.capture(r);
    assertThat(captured.response().httpStatus()).isEqualTo(422);
  }

  @Test
  void captureSystemFailedYields500() {
    CompleteRegistrationResult r =
        result(
            CompleteRegistrationResultStatus.SYSTEM_FAILED,
            WorkflowNextStep.SELECT_OTP_DESTINATION,
            false,
            false,
            List.of(),
            List.of());
    IdempotentCapturedResponse captured = codec.capture(r);
    assertThat(captured.response().httpStatus()).isEqualTo(500);
  }

  // ----- hydrate(): transport/status/payload validation -------------------------------

  @Test
  void hydrateNullSnapshotThrows() {
    assertThatThrownBy(() -> codec.hydrate(null)).isInstanceOf(NullPointerException.class);
  }

  @Test
  void hydrateMalformedJsonThrows() {
    IdempotentTerminalResponseSnapshot snapshot =
        new IdempotentTerminalResponseSnapshot(
            1, 200, "application/json", "{not valid json", Map.of(), null);
    assertThatThrownBy(() -> codec.hydrate(snapshot)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void hydrateWrongIdempotencyActionThrows() {
    // Build a syntactically-valid envelope wire with the WRONG action tag by
    // capturing via a DIFFERENT already-known action-tagged codec convention:
    // simplest reliable way is to hand-craft the JSON directly matching the
    // real EnvelopeWire/Payload shape but with an incorrect idempotencyAction.
    String wrongActionJson =
        "{\"idempotencyAction\":\"WRONG_ACTION\",\"payload\":{\"status\":\"REGISTERED\","
            + "\"nextStep\":\"RETURN_TO_LOGIN\",\"registered\":true,\"clearAuthFlowCookie\":true,"
            + "\"remainingTrusts\":[],\"errors\":[]}}";
    IdempotentTerminalResponseSnapshot snapshot =
        new IdempotentTerminalResponseSnapshot(
            1, 200, "application/json", wrongActionJson, Map.of(), null);
    assertThatThrownBy(() -> codec.hydrate(snapshot)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void hydrateNullPayloadThrows() {
    String nullPayloadJson = "{\"idempotencyAction\":\"COMPLETE\",\"payload\":null}";
    IdempotentTerminalResponseSnapshot snapshot =
        new IdempotentTerminalResponseSnapshot(
            1, 200, "application/json", nullPayloadJson, Map.of(), null);
    assertThatThrownBy(() -> codec.hydrate(snapshot)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void hydrateInvalidTrustInPayloadThrows() {
    CompleteRegistrationResult withBadTrust =
        result(
            CompleteRegistrationResultStatus.REGISTERED_WITH_REMAINING_TRUSTS,
            WorkflowNextStep.RETURN_TO_LOGIN,
            true,
            true,
            List.of(
                new RegistrationLookupAccount(
                    "",
                    "trust1",
                    "SCHEME_A",
                    "policy1",
                    "cert1",
                    "ACTIVE",
                    "2026-01-01",
                    true,
                    "cust1")),
            List.of());
    // capture() itself does not validate trust blankness (only hydrate()'s
    // reconstruction path does via the trust() helper), so directly craft a
    // snapshot with a blank accountEnv to exercise that specific guard.
    IdempotentCapturedResponse captured = codec.capture(withBadTrust);
    assertThatThrownBy(() -> codec.hydrate(captured.snapshot()))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
