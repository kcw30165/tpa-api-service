package com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyStartOutcome;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorDescriptor;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorSeverity;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorType;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowMessageDescriptor;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowMessageType;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentCapturedResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentTerminalResponseSnapshot;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.RegistrationUnregisteredTrust;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.VerifyRegistrationOtpResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.VerifyRegistrationOtpResultStatus;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Branch-oriented tests for {@link RegistrationOtpVerifyReplayCodec}. Both internal collaborators
 * ({@code RegistrationSharedResponseEnvelope}/{@code RegistrationSharedResponseBridge}) are
 * constructed internally (confirmed round 185, matching the already-proven
 * RegistrationCompleteReplayCodec sibling pattern) - exercised for real, not mocked.
 */
class RegistrationOtpVerifyReplayCodecTest {

  private final RegistrationOtpVerifyReplayCodec codec = new RegistrationOtpVerifyReplayCodec();

  private static VerifyRegistrationOtpResult result(
      VerifyRegistrationOtpResultStatus status,
      WorkflowNextStep nextStep,
      Integer remainingAttempts,
      List<RegistrationUnregisteredTrust> trusts,
      List<WorkflowMessageDescriptor> messages,
      List<WorkflowErrorDescriptor> errors) {
    return new VerifyRegistrationOtpResult(
        status, nextStep, trusts, remainingAttempts, messages, errors,
        RegistrationIdempotencyStartOutcome.STARTED, null);
  }

  private static RegistrationUnregisteredTrust trust() {
    return new RegistrationUnregisteredTrust("env1", "trust1");
  }

  private static WorkflowMessageDescriptor message() {
    return new WorkflowMessageDescriptor(WorkflowMessageType.INFO, "code1", "message1", "target1");
  }

  private static WorkflowErrorDescriptor error() {
    return new WorkflowErrorDescriptor(
        WorkflowErrorType.FIELD, "code2", "message2", List.of("field1"),
        WorkflowErrorSeverity.ERROR, "SERVER");
  }

  // ----- capture(): guard --------------------------------------------------------

  @Test
  void captureNullResultThrows() {
    assertThatThrownBy(() -> codec.capture(null)).isInstanceOf(NullPointerException.class);
  }

  // ----- capture(): success (with trusts/messages/errors) -> hydrate() round-trip -----

  @Test
  void captureAndHydrateRoundTripsVerifiedWithAllCollections() {
    VerifyRegistrationOtpResult original =
        result(
            VerifyRegistrationOtpResultStatus.VERIFIED, WorkflowNextStep.RETURN_TO_LOGIN, 3,
            List.of(trust()), List.of(message()), List.of(error()));
    IdempotentCapturedResponse captured = codec.capture(original);
    assertThat(captured.response().httpStatus()).isEqualTo(200);

    VerifyRegistrationOtpResult rehydrated = codec.hydrate(captured.snapshot());
    assertThat(rehydrated.status()).isEqualTo(VerifyRegistrationOtpResultStatus.VERIFIED);
    assertThat(rehydrated.nextStep()).isEqualTo(WorkflowNextStep.RETURN_TO_LOGIN);
    assertThat(rehydrated.remainingVerifyAttempts()).isEqualTo(3);
    assertThat(rehydrated.unregisteredTrusts()).hasSize(1);
    assertThat(rehydrated.unregisteredTrusts().get(0).trustCode()).isEqualTo("trust1");
    assertThat(rehydrated.messages()).hasSize(1);
    assertThat(rehydrated.messages().get(0).code()).isEqualTo("code1");
    assertThat(rehydrated.errors()).hasSize(1);
    assertThat(rehydrated.errors().get(0).code()).isEqualTo("code2");
  }

  @Test
  void captureAndHydrateRoundTripsWithEmptyCollections() {
    VerifyRegistrationOtpResult original =
        result(
            VerifyRegistrationOtpResultStatus.VERIFIED, WorkflowNextStep.RETURN_TO_LOGIN, null,
            List.of(), List.of(), List.of());
    IdempotentCapturedResponse captured = codec.capture(original);
    VerifyRegistrationOtpResult rehydrated = codec.hydrate(captured.snapshot());
    assertThat(rehydrated.unregisteredTrusts()).isEmpty();
    assertThat(rehydrated.messages()).isEmpty();
    assertThat(rehydrated.errors()).isEmpty();
    assertThat(rehydrated.remainingVerifyAttempts()).isNull();
  }

  // ----- capture(): each httpStatus() switch bucket ---------------------------------

  @Test
  void captureValidationFailedYields400() {
    VerifyRegistrationOtpResult r =
        result(
            VerifyRegistrationOtpResultStatus.VALIDATION_FAILED, WorkflowNextStep.SELECT_OTP_DESTINATION,
            null, List.of(), List.of(), List.of(error()));
    assertThat(codec.capture(r).response().httpStatus()).isEqualTo(400);
  }

  @Test
  void captureDuplicateInProgressYields409() {
    VerifyRegistrationOtpResult r =
        result(
            VerifyRegistrationOtpResultStatus.DUPLICATE_IN_PROGRESS, WorkflowNextStep.SELECT_OTP_DESTINATION,
            null, List.of(), List.of(), List.of());
    assertThat(codec.capture(r).response().httpStatus()).isEqualTo(409);
  }

  @Test
  void captureOtpExpiredYields422() {
    VerifyRegistrationOtpResult r =
        result(
            VerifyRegistrationOtpResultStatus.OTP_EXPIRED, WorkflowNextStep.SELECT_OTP_DESTINATION, null,
            List.of(), List.of(), List.of());
    assertThat(codec.capture(r).response().httpStatus()).isEqualTo(422);
  }

  @Test
  void captureSystemFailedYields500() {
    VerifyRegistrationOtpResult r =
        result(
            VerifyRegistrationOtpResultStatus.SYSTEM_FAILED, WorkflowNextStep.SELECT_OTP_DESTINATION, null,
            List.of(), List.of(), List.of());
    assertThat(codec.capture(r).response().httpStatus()).isEqualTo(500);
  }

  @Test
  void captureCompletedReplayStatusThrows() {
    VerifyRegistrationOtpResult lifecycleOnly =
        result(
            VerifyRegistrationOtpResultStatus.COMPLETED_REPLAY, WorkflowNextStep.RETURN_TO_LOGIN,
            null, List.of(), List.of(), List.of());
    assertThatThrownBy(() -> codec.capture(lifecycleOnly))
        .isInstanceOf(IllegalArgumentException.class);
  }

  // ----- hydrate(): guards --------------------------------------------------------

  @Test
  void hydrateNullSnapshotThrows() {
    assertThatThrownBy(() -> codec.hydrate(null)).isInstanceOf(NullPointerException.class);
  }

  @Test
  void hydrateMalformedJsonThrows() {
    IdempotentTerminalResponseSnapshot snapshot =
        new IdempotentTerminalResponseSnapshot(1, 200, "application/json", "{not valid json", Map.of(), null);
    assertThatThrownBy(() -> codec.hydrate(snapshot))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void hydrateNullPayloadThrows() {
    String nullPayloadJson = "{\"idempotencyAction\":\"OTP_VERIFY\",\"payload\":null}";
    IdempotentTerminalResponseSnapshot snapshot =
        new IdempotentTerminalResponseSnapshot(1, 200, "application/json", nullPayloadJson, Map.of(), null);
    assertThatThrownBy(() -> codec.hydrate(snapshot))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void hydrateInvalidTrustInPayloadThrows() {
    VerifyRegistrationOtpResult withBlankTrustField =
        result(
            VerifyRegistrationOtpResultStatus.VERIFIED, WorkflowNextStep.RETURN_TO_LOGIN, null,
            List.of(new RegistrationUnregisteredTrust("", "trust1")), List.of(), List.of());
    IdempotentCapturedResponse captured = codec.capture(withBlankTrustField);
    assertThatThrownBy(() -> codec.hydrate(captured.snapshot()))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
