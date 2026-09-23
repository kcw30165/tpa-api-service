package com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyStartOutcome;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorDescriptor;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorSeverity;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorType;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowMessageDescriptor;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowMessageType;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.idempotency.HydratedIdempotentResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentCapturedResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentTerminalResponseSnapshot;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.RegistrationUnregisteredTrust;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.VerifyRegistrationOtpResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.VerifyRegistrationOtpResultStatus;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

/**
 * Branch-oriented tests for {@link RegistrationOtpVerifySharedExecutionBridge}. Uses a mocked
 * {@link RegistrationOtpVerifyReplayCodec} (a concrete, internally-constructing class, not an
 * interface). All types verified verbatim (round 158).
 */
class RegistrationOtpVerifySharedExecutionBridgeTest {

  private final RegistrationOtpVerifyReplayCodec codec =
      mock(RegistrationOtpVerifyReplayCodec.class);

  private RegistrationOtpVerifySharedExecutionBridge newBridge() {
    return new RegistrationOtpVerifySharedExecutionBridge(codec);
  }

  private static VerifyRegistrationOtpResult verified() {
    return new VerifyRegistrationOtpResult(
        VerifyRegistrationOtpResultStatus.VERIFIED,
        WorkflowNextStep.RETURN_TO_LOGIN,
        List.of(new RegistrationUnregisteredTrust("env1", "trust1")),
        3,
        List.of(
            new WorkflowMessageDescriptor(WorkflowMessageType.INFO, "code1", "msg1", "target1")),
        List.of(
            new WorkflowErrorDescriptor(
                WorkflowErrorType.FIELD,
                "code2",
                "msg2",
                List.of("field1"),
                WorkflowErrorSeverity.ERROR,
                "SERVER")),
        RegistrationIdempotencyStartOutcome.STARTED,
        null);
  }

  // ----- constructor guard --------------------------------------------------

  @Test
  void constructorRejectsNullCodec() {
    assertThatThrownBy(() -> new RegistrationOtpVerifySharedExecutionBridge(null))
        .isInstanceOf(NullPointerException.class);
  }

  // ----- toTransientResponse() / fromTransientResponse() round-trip ------------------

  @Test
  void toTransientResponseThenFromTransientResponseRoundTrips() {
    RegistrationOtpVerifySharedExecutionBridge bridge = newBridge();
    HydratedIdempotentResponse response = bridge.toTransientResponse(verified());
    assertThat(response.httpStatus()).isEqualTo(200);
    VerifyRegistrationOtpResult roundTripped = bridge.fromTransientResponse(response);
    assertThat(roundTripped.status()).isEqualTo(VerifyRegistrationOtpResultStatus.VERIFIED);
    assertThat(roundTripped.unregisteredTrusts()).hasSize(1);
    assertThat(roundTripped.messages()).hasSize(1);
    assertThat(roundTripped.errors()).hasSize(1);
  }

  @Test
  void toTransientResponseThrowsForLifecycleOnlyStatus() {
    VerifyRegistrationOtpResult lifecycleOnly =
        new VerifyRegistrationOtpResult(
            VerifyRegistrationOtpResultStatus.COMPLETED_REPLAY,
            WorkflowNextStep.RETURN_TO_LOGIN,
            List.of(),
            null,
            List.of(),
            List.of(),
            null,
            null);
    assertThatThrownBy(() -> newBridge().toTransientResponse(lifecycleOnly))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void fromTransientResponseWrongMediaTypeThrows() {
    HydratedIdempotentResponse response =
        new HydratedIdempotentResponse(200, "text/plain", "{}", Map.of());
    assertThatThrownBy(() -> newBridge().fromTransientResponse(response))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void fromTransientResponseMalformedBodyThrows() {
    HydratedIdempotentResponse response =
        new HydratedIdempotentResponse(200, "application/json", "{not valid json", Map.of());
    assertThatThrownBy(() -> newBridge().fromTransientResponse(response))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void fromTransientResponseWithNullOptionalFieldsRoundTrips() {
    RegistrationOtpVerifySharedExecutionBridge bridge = newBridge();
    VerifyRegistrationOtpResult minimal =
        new VerifyRegistrationOtpResult(
            VerifyRegistrationOtpResultStatus.VALIDATION_FAILED,
            WorkflowNextStep.SELECT_OTP_DESTINATION,
            List.of(),
            null,
            List.of(),
            List.of(),
            null,
            null);
    HydratedIdempotentResponse response = bridge.toTransientResponse(minimal);
    VerifyRegistrationOtpResult roundTripped = bridge.fromTransientResponse(response);
    assertThat(roundTripped.remainingVerifyAttempts()).isNull();
    assertThat(roundTripped.idempotencyOutcome()).isNull();
  }

  // ----- capture() / hydrate() delegate to the codec --------------------------------

  @Test
  void captureDelegatesToCodec() {
    IdempotentCapturedResponse captured = mock(IdempotentCapturedResponse.class);
    when(codec.capture(verified())).thenReturn(captured);
    StepVerifier.create(newBridge().capture(newBridge().toTransientResponse(verified())))
        .expectNext(captured)
        .verifyComplete();
  }

  @Test
  void hydrateDelegatesToCodecAndMapsResult() {
    IdempotentTerminalResponseSnapshot snapshot = mock(IdempotentTerminalResponseSnapshot.class);
    when(codec.hydrate(snapshot)).thenReturn(verified());
    StepVerifier.create(newBridge().hydrate(snapshot))
        .assertNext(response -> assertThat(response.httpStatus()).isEqualTo(200))
        .verifyComplete();
  }
}
