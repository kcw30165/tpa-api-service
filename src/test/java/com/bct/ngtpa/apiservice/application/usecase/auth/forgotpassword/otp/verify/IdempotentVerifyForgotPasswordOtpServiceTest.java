package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.verify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.idempotency.HydratedIdempotentResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyExecutor;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyFailureClassification;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyFingerprint;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyOperationId;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyReservationOutcome;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentExecutionCommand;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentExecutionResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyAction;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyIntegrationPolicy;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.otpverify.ForgotPasswordOtpVerifyExecutionBridge;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class IdempotentVerifyForgotPasswordOtpServiceTest {
  @Test
  void commandUsesSessionScopeConstantFingerprintTtlAndConservativeClassifier() {
    Fixture fixture = new Fixture();
    when(fixture.policy.operationId(any(), any(), any()))
        .thenReturn(mock(IdempotencyOperationId.class));
    when(fixture.policy.otpVerify()).thenReturn(mock(IdempotencyFingerprint.class));

    assertThat(fixture.service.verify(command()).block().status())
        .isEqualTo(VerifyForgotPasswordOtpStatus.DUPLICATE_IN_PROGRESS);
    verify(fixture.policy)
        .operationId(
            ForgotPasswordIdempotencyAction.OTP_VERIFY, "afs_verify_123", "idem-verify-123");
    verify(fixture.policy).otpVerify();
    assertThat(fixture.executor.command.ttl()).isEqualTo(Duration.ofMinutes(5));
    assertThat(fixture.executor.command.failureClassifier().classify(new RuntimeException()))
        .isEqualTo(IdempotencyFailureClassification.POSSIBLE_SIDE_EFFECT);
    verify(fixture.operation, never()).execute(any());
  }

  @Test
  void acquiredExecutesProtectedOperationAndCompletedReplayDoesNot() {
    Fixture acquired = new Fixture();
    acquired.executor.invokeProtected = true;
    when(acquired.policy.operationId(any(), any(), any()))
        .thenReturn(mock(IdempotencyOperationId.class));
    when(acquired.policy.otpVerify()).thenReturn(mock(IdempotencyFingerprint.class));
    VerifyForgotPasswordOtpResult verified = result(VerifyForgotPasswordOtpStatus.VERIFIED);
    HydratedIdempotentResponse response = response();
    when(acquired.operation.execute(command()))
        .thenReturn(Mono.just(new ForgotPasswordOtpVerifyProtectedExecutionResult(verified)));
    when(acquired.responses.toTransientResponse(verified)).thenReturn(response);
    when(acquired.responses.fromTransientResponse(response)).thenReturn(verified);
    StepVerifier.create(acquired.service.verify(command())).expectNext(verified).verifyComplete();
    verify(acquired.operation).execute(command());

    Fixture replay = new Fixture();
    replay.executor.outcome = IdempotencyReservationOutcome.COMPLETED_REPLAY;
    replay.executor.terminal = response;
    when(replay.policy.operationId(any(), any(), any()))
        .thenReturn(mock(IdempotencyOperationId.class));
    when(replay.policy.otpVerify()).thenReturn(mock(IdempotencyFingerprint.class));
    when(replay.responses.fromTransientResponse(response)).thenReturn(verified);
    StepVerifier.create(replay.service.verify(command())).expectNext(verified).verifyComplete();
    verify(replay.operation, never()).execute(any());
  }

  @Test
  void mapsEveryExistingLifecycleOutcomeWithoutProtectedExecution() {
    assertMapped(
        IdempotencyReservationOutcome.DUPLICATE_IN_PROGRESS,
        VerifyForgotPasswordOtpStatus.DUPLICATE_IN_PROGRESS);
    assertMapped(
        IdempotencyReservationOutcome.KEY_REUSED_WITH_DIFFERENT_REQUEST,
        VerifyForgotPasswordOtpStatus.IDEMPOTENCY_KEY_REUSED);
    assertMapped(
        IdempotencyReservationOutcome.FAILED_EXISTING, VerifyForgotPasswordOtpStatus.SYSTEM_FAILED);
    assertMapped(
        IdempotencyReservationOutcome.UNKNOWN_OUTCOME_EXISTING,
        VerifyForgotPasswordOtpStatus.SYSTEM_FAILED);
  }

  @Test
  void protectedSystemFailureCrossesExecutorBoundaryBeforeCapture() {
    Fixture fixture = new Fixture();
    fixture.executor.invokeProtected = true;
    when(fixture.policy.operationId(any(), any(), any()))
        .thenReturn(mock(IdempotencyOperationId.class));
    when(fixture.policy.otpVerify()).thenReturn(mock(IdempotencyFingerprint.class));
    when(fixture.operation.execute(any()))
        .thenReturn(
            Mono.just(
                new ForgotPasswordOtpVerifyProtectedExecutionResult(
                    result(VerifyForgotPasswordOtpStatus.SYSTEM_FAILED))));
    assertThat(fixture.service.verify(command()).block().status())
        .isEqualTo(VerifyForgotPasswordOtpStatus.SYSTEM_FAILED);
    verify(fixture.responses, never()).toTransientResponse(any());
  }

  private static void assertMapped(
      IdempotencyReservationOutcome outcome, VerifyForgotPasswordOtpStatus expected) {
    Fixture fixture = new Fixture();
    fixture.executor.outcome = outcome;
    when(fixture.policy.operationId(any(), any(), any()))
        .thenReturn(mock(IdempotencyOperationId.class));
    when(fixture.policy.otpVerify()).thenReturn(mock(IdempotencyFingerprint.class));
    assertThat(fixture.service.verify(command()).block().status()).isEqualTo(expected);
    verify(fixture.operation, never()).execute(any());
  }

  private static VerifyForgotPasswordOtpCommand command() {
    return new VerifyForgotPasswordOtpCommand(
        new AuthFlowSessionId("afs_verify_123"),
        "csrf",
        "123456",
        "https://portal.example",
        "https://portal.example/page",
        "idem-verify-123",
        "en",
        "req-verify-123",
        Duration.ofMinutes(5));
  }

  private static VerifyForgotPasswordOtpResult result(VerifyForgotPasswordOtpStatus status) {
    return new VerifyForgotPasswordOtpResult(
        status,
        status == VerifyForgotPasswordOtpStatus.VERIFIED
            ? WorkflowNextStep.PASSWORD_SETUP
            : WorkflowNextStep.NONE,
        null);
  }

  private static HydratedIdempotentResponse response() {
    return new HydratedIdempotentResponse(200, "application/json", "{}", Map.of());
  }

  private static final class CapturingExecutor implements IdempotencyExecutor {
    private IdempotentExecutionCommand command;
    private IdempotencyReservationOutcome outcome =
        IdempotencyReservationOutcome.DUPLICATE_IN_PROGRESS;
    private HydratedIdempotentResponse terminal;
    private boolean invokeProtected;

    @Override
    public Mono<IdempotentExecutionResult> execute(IdempotentExecutionCommand supplied) {
      command = supplied;
      if (invokeProtected) {
        return supplied
            .protectedOperation()
            .get()
            .map(
                response ->
                    new IdempotentExecutionResult(
                        IdempotencyReservationOutcome.ACQUIRED, response));
      }
      return Mono.just(new IdempotentExecutionResult(outcome, terminal));
    }
  }

  private static final class Fixture {
    private final CapturingExecutor executor = new CapturingExecutor();
    private final ForgotPasswordOtpVerifyProtectedOperation operation =
        mock(ForgotPasswordOtpVerifyProtectedOperation.class);
    private final ForgotPasswordOtpVerifyExecutionBridge responses =
        mock(ForgotPasswordOtpVerifyExecutionBridge.class);
    private final ForgotPasswordIdempotencyIntegrationPolicy policy =
        mock(ForgotPasswordIdempotencyIntegrationPolicy.class);
    private final IdempotentVerifyForgotPasswordOtpService service =
        new IdempotentVerifyForgotPasswordOtpService(executor, operation, responses, policy);
  }
}
