package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.resend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.idempotency.HydratedIdempotentResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyExecutor;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyOperationId;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyReservationOutcome;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentExecutionCommand;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentExecutionResult;
import com.bct.ngtpa.apiservice.application.idempotency.ProtectedReplayReference;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyIntegrationPolicy;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.otpresend.ForgotPasswordOtpResendExecutionBridge;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Branch-oriented reactive tests for {@link IdempotentResendForgotPasswordOtpService}. Mirrors the
 * proven ArgumentCaptor + direct-supplier-invocation technique from
 * IdempotentCompleteForgotPasswordServiceTest (round 58) to exercise executeAcquired.
 */
class IdempotentResendForgotPasswordOtpServiceTest {

  private final IdempotencyExecutor executor = mock(IdempotencyExecutor.class);
  private final ForgotPasswordOtpResendProtectedOperation operation =
      mock(ForgotPasswordOtpResendProtectedOperation.class);
  private final ForgotPasswordOtpResendExecutionBridge responses =
      mock(ForgotPasswordOtpResendExecutionBridge.class);
  private final ForgotPasswordIdempotencyIntegrationPolicy policy =
      mock(ForgotPasswordIdempotencyIntegrationPolicy.class);
  private final Duration ttl = Duration.ofMinutes(5);

  private final AuthFlowSessionId sid = new AuthFlowSessionId("afs-idem-resend-1");

  private IdempotentResendForgotPasswordOtpService newService() {
    return new IdempotentResendForgotPasswordOtpService(executor, operation, responses, policy, ttl);
  }

  @Test
  void nonPositiveTtlThrows() {
    assertThatThrownBy(
            () ->
                new IdempotentResendForgotPasswordOtpService(
                    executor, operation, responses, policy, Duration.ZERO))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private ResendForgotPasswordOtpCommand command() {
    return new ResendForgotPasswordOtpCommand(
        sid, "csrf-token", "https://portal.bcthk.com", "https://portal.bcthk.com/resend",
        "req-1", "en", "idem-1");
  }

  @Test
  void nullCommandThrows() {
    assertThatThrownBy(() -> newService().resend(null)).isInstanceOf(NullPointerException.class);
  }

  private void stubPolicyIdentity() {
    when(policy.operationId(any(), any(), any())).thenReturn(mock(IdempotencyOperationId.class));
    when(policy.otpResend()).thenReturn(mock(com.bct.ngtpa.apiservice.application.idempotency.IdempotencyFingerprint.class));
  }

  /** Captures the command passed to executor.execute(...) and returns it. */
  private IdempotentExecutionCommand captureExecutionCommand() {
    stubPolicyIdentity();
    ArgumentCaptor<IdempotentExecutionCommand> captor =
        ArgumentCaptor.forClass(IdempotentExecutionCommand.class);
    when(executor.execute(captor.capture()))
        .thenReturn(
            Mono.just(
                new IdempotentExecutionResult(
                    IdempotencyReservationOutcome.ACQUIRED, mock(HydratedIdempotentResponse.class))));
    newService().resend(command()).block();
    return captor.getValue();
  }

  // ----- direct protectedOperation supplier tests (executeAcquired) -----------

  @Test
  void protectedOperationSystemFailedErrors() {
    ResendForgotPasswordOtpResult failed =
        new ResendForgotPasswordOtpResult(ResendForgotPasswordOtpStatus.SYSTEM_FAILED, null, null, null, null, null);
    when(operation.execute(any()))
        .thenReturn(
            Mono.just(
                new ForgotPasswordOtpResendProtectedExecutionResult(failed, null)));
    IdempotentExecutionCommand cmd = captureExecutionCommand();
    StepVerifier.create(cmd.protectedOperation().get())
        .expectError(IllegalStateException.class)
        .verify();
  }

  @Test
  void protectedOperationSuccessWithReplayReferenceAttaches() {
    ResendForgotPasswordOtpResult resent =
        new ResendForgotPasswordOtpResult(ResendForgotPasswordOtpStatus.RESENT, "j***@example.com", 300, 60, 4, null);
    ProtectedReplayReference reference = new ProtectedReplayReference("replay-ref-1234567890abcd");
    when(operation.execute(any()))
        .thenReturn(
            Mono.just(new ForgotPasswordOtpResendProtectedExecutionResult(resent, reference)));
    HydratedIdempotentResponse baseResponse = mock(HydratedIdempotentResponse.class);
    HydratedIdempotentResponse withRef = mock(HydratedIdempotentResponse.class);
    when(responses.toTransientResponse(resent)).thenReturn(baseResponse);
    when(baseResponse.withProtectedReplayReference(reference)).thenReturn(withRef);
    IdempotentExecutionCommand cmd = captureExecutionCommand();
    StepVerifier.create(cmd.protectedOperation().get()).expectNext(withRef).verifyComplete();
  }

  @Test
  void protectedOperationSuccessWithoutReplayReferenceReturnsBase() {
    ResendForgotPasswordOtpResult securityRejected =
        new ResendForgotPasswordOtpResult(
            ResendForgotPasswordOtpStatus.SECURITY_REJECTED, null, null, null, null, null);
    when(operation.execute(any()))
        .thenReturn(
            Mono.just(new ForgotPasswordOtpResendProtectedExecutionResult(securityRejected, null)));
    HydratedIdempotentResponse baseResponse = mock(HydratedIdempotentResponse.class);
    when(responses.toTransientResponse(securityRejected)).thenReturn(baseResponse);
    IdempotentExecutionCommand cmd = captureExecutionCommand();
    StepVerifier.create(cmd.protectedOperation().get()).expectNext(baseResponse).verifyComplete();
  }

  // ----- route() outcome-switch branches (via canned executor results) --------------

  @Test
  void acquiredDelegatesToResponsesFromTransientResponse() {
    stubPolicyIdentity();
    HydratedIdempotentResponse response = mock(HydratedIdempotentResponse.class);
    when(executor.execute(any()))
        .thenReturn(
            Mono.just(new IdempotentExecutionResult(IdempotencyReservationOutcome.ACQUIRED, response)));
    ResendForgotPasswordOtpResult expected =
        new ResendForgotPasswordOtpResult(ResendForgotPasswordOtpStatus.RESENT, "j***@example.com", 300, 60, 4, null);
    when(responses.fromTransientResponse(response)).thenReturn(expected);
    StepVerifier.create(newService().resend(command())).expectNext(expected).verifyComplete();
  }

  @Test
  void duplicateInProgressYieldsSystemFailed() {
    stubPolicyIdentity();
    when(executor.execute(any()))
        .thenReturn(
            Mono.just(
                new IdempotentExecutionResult(
                    IdempotencyReservationOutcome.DUPLICATE_IN_PROGRESS, null)));
    StepVerifier.create(newService().resend(command()))
        .assertNext(r -> assertThat(r.status()).isEqualTo(ResendForgotPasswordOtpStatus.SYSTEM_FAILED))
        .verifyComplete();
  }

  @Test
  void keyReusedYieldsIdempotencyKeyReused() {
    stubPolicyIdentity();
    when(executor.execute(any()))
        .thenReturn(
            Mono.just(
                new IdempotentExecutionResult(
                    IdempotencyReservationOutcome.KEY_REUSED_WITH_DIFFERENT_REQUEST, null)));
    StepVerifier.create(newService().resend(command()))
        .assertNext(
            r -> assertThat(r.status()).isEqualTo(ResendForgotPasswordOtpStatus.IDEMPOTENCY_KEY_REUSED))
        .verifyComplete();
  }

  @Test
  void failedExistingYieldsPreviousFailed() {
    stubPolicyIdentity();
    when(executor.execute(any()))
        .thenReturn(
            Mono.just(new IdempotentExecutionResult(IdempotencyReservationOutcome.FAILED_EXISTING, null)));
    StepVerifier.create(newService().resend(command()))
        .assertNext(r -> assertThat(r.status()).isEqualTo(ResendForgotPasswordOtpStatus.PREVIOUS_FAILED))
        .verifyComplete();
  }

  @Test
  void unknownOutcomeExistingYieldsUnknownOutcome() {
    stubPolicyIdentity();
    when(executor.execute(any()))
        .thenReturn(
            Mono.just(
                new IdempotentExecutionResult(
                    IdempotencyReservationOutcome.UNKNOWN_OUTCOME_EXISTING, null)));
    StepVerifier.create(newService().resend(command()))
        .assertNext(r -> assertThat(r.status()).isEqualTo(ResendForgotPasswordOtpStatus.UNKNOWN_OUTCOME))
        .verifyComplete();
  }

  @Test
  void executorErrorYieldsSystemFailed() {
    stubPolicyIdentity();
    when(executor.execute(any())).thenReturn(Mono.error(new IllegalStateException("boom")));
    StepVerifier.create(newService().resend(command()))
        .assertNext(r -> assertThat(r.status()).isEqualTo(ResendForgotPasswordOtpStatus.SYSTEM_FAILED))
        .verifyComplete();
  }
}
