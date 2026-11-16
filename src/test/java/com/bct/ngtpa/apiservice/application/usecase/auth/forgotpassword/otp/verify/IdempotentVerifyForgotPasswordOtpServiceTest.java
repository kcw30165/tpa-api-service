package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.verify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.dto.observability.FailureEvent;
import com.bct.ngtpa.apiservice.application.dto.observability.FailureOrigin;
import com.bct.ngtpa.apiservice.application.idempotency.HydratedIdempotentResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyExecutor;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyFailureClassification;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyFingerprint;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyOperationId;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyReservationOutcome;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentExecutionCommand;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentExecutionResult;
import com.bct.ngtpa.apiservice.application.port.out.FailureEventPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyAction;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyIntegrationPolicy;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.otpverify.ForgotPasswordOtpVerifyExecutionBridge;
import java.time.Duration;
import java.util.Map;
import org.mockito.ArgumentCaptor;
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
        .thenReturn(Mono.just(new ForgotPasswordOtpVerifyProtectedExecutionResult(verified, null)));
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
                    result(VerifyForgotPasswordOtpStatus.SYSTEM_FAILED),
                    ForgotPasswordOtpVerifyIncident.PROTECTED_OPERATION)));
    assertThat(fixture.service.verify(command()).block().status())
        .isEqualTo(VerifyForgotPasswordOtpStatus.SYSTEM_FAILED);
    verify(fixture.responses, never()).toTransientResponse(any());
  }

  @Test
  void deferredLockReleaseFailureDoesNotPublishOrCreateReceipt() {
    Fixture fixture = new Fixture();
    fixture.executor.invokeProtected = true;
    when(fixture.policy.operationId(any(), any(), any()))
        .thenReturn(mock(IdempotencyOperationId.class));
    when(fixture.policy.otpVerify()).thenReturn(mock(IdempotencyFingerprint.class));
    when(fixture.operation.execute(any()))
        .thenReturn(
            Mono.just(
                new ForgotPasswordOtpVerifyProtectedExecutionResult(
                    result(VerifyForgotPasswordOtpStatus.SYSTEM_FAILED),
                    ForgotPasswordOtpVerifyIncident.LOCK_RELEASE)));

    StepVerifier.create(serviceWithFailureEvents(fixture).verify(command()))
        .assertNext(
            response -> {
              assertThat(response.status()).isEqualTo(VerifyForgotPasswordOtpStatus.SYSTEM_FAILED);
              assertThat(response.publicationReceipt()).isNull();
            })
        .verifyComplete();

    verifyNoFailureEvents(fixture.failureEvents);
  }

  @Test
  void acquiredProtectedFailurePublishesOneCauseFreeLocalSystemIncident() {
    Fixture fixture = new Fixture();
    fixture.executor.invokeProtected = true;
    when(fixture.policy.operationId(any(), any(), any()))
        .thenReturn(mock(IdempotencyOperationId.class));
    when(fixture.policy.otpVerify()).thenReturn(mock(IdempotencyFingerprint.class));
    when(fixture.operation.execute(any()))
        .thenReturn(Mono.error(new IllegalStateException("sensitive-protected-message")));

    StepVerifier.create(serviceWithFailureEvents(fixture).verify(command()))
        .assertNext(
            result -> {
              assertThat(result.status()).isEqualTo(VerifyForgotPasswordOtpStatus.SYSTEM_FAILED);
              assertThat(result.publicationReceipt()).isNotNull();
              assertThat(result.toString()).doesNotContain("sensitive-protected-message");
            })
        .verifyComplete();

    ArgumentCaptor<FailureEvent> event = ArgumentCaptor.forClass(FailureEvent.class);
    verify(fixture.failureEvents).publish(event.capture());
    assertThat(event.getValue().event()).isEqualTo("forgot-password.otp-verify.failed");
    assertThat(event.getValue().capability()).isEqualTo("FORGOT_PASSWORD");
    assertThat(event.getValue().operation()).isEqualTo("OTP_VERIFY");
    assertThat(event.getValue().origin()).isEqualTo(FailureOrigin.LOCAL_SYSTEM);
    assertThat(event.getValue().internalCode()).isEqualTo("OTP_VERIFY_TECHNICAL_FAILURE");
    assertThat(event.getValue().safeDimensions()).containsEntry("stage", "protected-operation");
    assertThat(event.getValue().requestId()).isEqualTo("req-verify-123");
    assertThat(event.getValue().toString()).doesNotContain("sensitive-protected-message");
    verify(
            fixture.failureEvents,
            never())
        .publish(any(FailureEvent.class), any(Throwable.class));
  }

  @Test
  void independentExecutorFailurePublishesSeparateIncident() {
    Fixture fixture = new Fixture();
    fixture.executor.executeFailure = new IllegalStateException("store-secret");
    when(fixture.policy.operationId(any(), any(), any()))
        .thenReturn(mock(IdempotencyOperationId.class));
    when(fixture.policy.otpVerify()).thenReturn(mock(IdempotencyFingerprint.class));

    StepVerifier.create(serviceWithFailureEvents(fixture).verify(command()))
        .assertNext(
            result -> {
              assertThat(result.status()).isEqualTo(VerifyForgotPasswordOtpStatus.SYSTEM_FAILED);
              assertThat(result.publicationReceipt()).isNotNull();
            })
        .verifyComplete();

    ArgumentCaptor<FailureEvent> event = ArgumentCaptor.forClass(FailureEvent.class);
    verify(fixture.failureEvents).publish(event.capture());
    assertThat(event.getValue().internalCode()).isEqualTo("OTP_VERIFY_IDEMPOTENCY_FAILURE");
    assertThat(event.getValue().safeDimensions()).containsEntry("stage", "idempotency");
    assertThat(event.getValue().toString()).doesNotContain("store-secret");
    verify(fixture.operation, never()).execute(any());
  }

  @Test
  void synchronousSetupFailurePublishesIndependentIncident() {
    Fixture fixture = new Fixture();
    when(fixture.policy.operationId(any(), any(), any()))
        .thenThrow(new IllegalStateException("setup-secret"));

    StepVerifier.create(fixture.service.verify(command()))
        .assertNext(
            result -> {
              assertThat(result.status()).isEqualTo(VerifyForgotPasswordOtpStatus.SYSTEM_FAILED);
              assertThat(result.publicationReceipt()).isNotNull();
            })
        .verifyComplete();

    ArgumentCaptor<FailureEvent> event = ArgumentCaptor.forClass(FailureEvent.class);
    verify(fixture.failureEvents).publish(event.capture());
    assertThat(event.getValue().internalCode()).isEqualTo("OTP_VERIFY_IDEMPOTENCY_FAILURE");
    assertThat(event.getValue().safeDimensions()).containsEntry("stage", "idempotency");
    assertThat(event.getValue().toString()).doesNotContain("setup-secret");
    assertThat(fixture.executor.command).isNull();
  }

  @Test
  void synchronousExecutorFailurePublishesIndependentIncident() {
    Fixture fixture = new Fixture();
    fixture.executor.synchronousExecuteFailure = new IllegalStateException("execute-secret");
    when(fixture.policy.operationId(any(), any(), any()))
        .thenReturn(mock(IdempotencyOperationId.class));
    when(fixture.policy.otpVerify()).thenReturn(mock(IdempotencyFingerprint.class));

    StepVerifier.create(fixture.service.verify(command()))
        .assertNext(
            result -> {
              assertThat(result.status()).isEqualTo(VerifyForgotPasswordOtpStatus.SYSTEM_FAILED);
              assertThat(result.publicationReceipt()).isNotNull();
            })
        .verifyComplete();

    ArgumentCaptor<FailureEvent> event = ArgumentCaptor.forClass(FailureEvent.class);
    verify(fixture.failureEvents).publish(event.capture());
    assertThat(event.getValue().internalCode()).isEqualTo("OTP_VERIFY_IDEMPOTENCY_FAILURE");
    assertThat(event.getValue().toString()).doesNotContain("execute-secret");
  }

  @Test
  void independentExecutorFailureAfterProtectedIncidentPublishesBoth() {
    Fixture fixture = new Fixture();
    fixture.executor.invokeProtected = true;
    fixture.executor.recoveryFailure = new IllegalStateException("recovery-store-secret");
    when(fixture.policy.operationId(any(), any(), any()))
        .thenReturn(mock(IdempotencyOperationId.class));
    when(fixture.policy.otpVerify()).thenReturn(mock(IdempotencyFingerprint.class));
    when(fixture.operation.execute(any()))
        .thenReturn(Mono.error(new IllegalStateException("protected-secret")));

    StepVerifier.create(serviceWithFailureEvents(fixture).verify(command()))
        .assertNext(result -> assertThat(result.publicationReceipt()).isNotNull())
        .verifyComplete();

    ArgumentCaptor<FailureEvent> events = ArgumentCaptor.forClass(FailureEvent.class);
    verify(fixture.failureEvents, times(2)).publish(events.capture());
    assertThat(events.getAllValues())
        .extracting(FailureEvent::internalCode)
        .containsExactly("OTP_VERIFY_TECHNICAL_FAILURE", "OTP_VERIFY_IDEMPOTENCY_FAILURE");
  }

  @Test
  void publicationFailureDoesNotCreateReceiptOrRetryPublication() {
    Fixture fixture = new Fixture();
    fixture.executor.invokeProtected = true;
    when(fixture.policy.operationId(any(), any(), any()))
        .thenReturn(mock(IdempotencyOperationId.class));
    when(fixture.policy.otpVerify()).thenReturn(mock(IdempotencyFingerprint.class));
    when(fixture.operation.execute(any()))
        .thenReturn(Mono.error(new IllegalStateException("protected-secret")));
    org.mockito.Mockito.doThrow(new IllegalStateException("publication-secret"))
        .when(fixture.failureEvents)
        .publish(any(FailureEvent.class));

    StepVerifier.create(serviceWithFailureEvents(fixture).verify(command())).expectError().verify();

    verify(fixture.failureEvents, times(1)).publish(any(FailureEvent.class));
    verify(fixture.failureEvents, never()).publish(any(FailureEvent.class), any(Throwable.class));
  }

  @Test
  void completedAndUncertainReplaysDoNotPublishOrCarryReceipt() {
    for (IdempotencyReservationOutcome outcome :
        new IdempotencyReservationOutcome[] {
          IdempotencyReservationOutcome.COMPLETED_REPLAY,
          IdempotencyReservationOutcome.FAILED_EXISTING,
          IdempotencyReservationOutcome.UNKNOWN_OUTCOME_EXISTING
        }) {
      Fixture fixture = new Fixture();
      fixture.executor.outcome = outcome;
      fixture.executor.terminal = response();
      when(fixture.policy.operationId(any(), any(), any()))
          .thenReturn(mock(IdempotencyOperationId.class));
      when(fixture.policy.otpVerify()).thenReturn(mock(IdempotencyFingerprint.class));
      when(fixture.responses.fromTransientResponse(any()))
          .thenReturn(result(VerifyForgotPasswordOtpStatus.OTP_INVALID));

      StepVerifier.create(serviceWithFailureEvents(fixture).verify(command()))
          .assertNext(
              result -> {
                assertThat(result.publicationReceipt()).isNull();
                if (outcome != IdempotencyReservationOutcome.COMPLETED_REPLAY) {
                  assertThat(result.status())
                      .isEqualTo(VerifyForgotPasswordOtpStatus.SYSTEM_FAILED);
                }
              })
          .verifyComplete();

      verify(fixture.operation, never()).execute(any());
      verifyNoFailureEvents(fixture.failureEvents);
    }
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
        null, null);
  }

  private static HydratedIdempotentResponse response() {
    return new HydratedIdempotentResponse(200, "application/json", "{}", Map.of());
  }

  private static IdempotentVerifyForgotPasswordOtpService serviceWithFailureEvents(Fixture fixture) {
    return fixture.service;
  }

  private static void verifyNoFailureEvents(FailureEventPort failureEvents) {
    org.mockito.Mockito.verifyNoInteractions(failureEvents);
  }

  private static final class CapturingExecutor implements IdempotencyExecutor {
    private IdempotentExecutionCommand command;
    private IdempotencyReservationOutcome outcome =
        IdempotencyReservationOutcome.DUPLICATE_IN_PROGRESS;
    private HydratedIdempotentResponse terminal;
    private boolean invokeProtected;
    private RuntimeException executeFailure;
    private RuntimeException synchronousExecuteFailure;
    private RuntimeException recoveryFailure;

    @Override
    public Mono<IdempotentExecutionResult> execute(IdempotentExecutionCommand supplied) {
      command = supplied;
      if (synchronousExecuteFailure != null) {
        throw synchronousExecuteFailure;
      }
      if (executeFailure != null) {
        return Mono.error(executeFailure);
      }
      if (invokeProtected) {
        return supplied
            .protectedOperation()
            .get()
            .map(
                response ->
                    new IdempotentExecutionResult(
                        IdempotencyReservationOutcome.ACQUIRED, response))
            .onErrorResume(
                error ->
                    recoveryFailure == null ? Mono.error(error) : Mono.error(recoveryFailure));
      }
      return Mono.just(new IdempotentExecutionResult(outcome, terminal));
    }

    @Override
    public Mono<java.util.Optional<IdempotentExecutionResult>> replayCompleted(
        com.bct.ngtpa.apiservice.application.idempotency.IdempotencyOperationId operationId,
        com.bct.ngtpa.apiservice.application.idempotency.IdempotencyFingerprint fingerprint,
        com.bct.ngtpa.apiservice.application.idempotency.ProtectedReplayResolver replayResolver) {
      return Mono.error(new AssertionError("Unexpected replay-only lookup"));
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
    private final FailureEventPort failureEvents = mock(FailureEventPort.class);
    private final IdempotentVerifyForgotPasswordOtpService service =
        new IdempotentVerifyForgotPasswordOtpService(
            executor, operation, responses, policy, failureEvents);
  }
}
