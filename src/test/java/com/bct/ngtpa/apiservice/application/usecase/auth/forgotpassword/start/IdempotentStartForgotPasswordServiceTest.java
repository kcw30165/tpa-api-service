package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.start;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyKey;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.idempotency.HydratedIdempotentResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyFailureClassification;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyFingerprint;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyOperationId;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyReservationOutcome;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentExecutionCommand;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentExecutionResult;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyExecutor;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyAction;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyIntegrationPolicy;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.start.ForgotPasswordStartExecutionBridge;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class IdempotentStartForgotPasswordServiceTest {
  @Test
  void commandUsesNormalizedFingerprintStableAnonymousScopeTtlAndConservativeClassifier() {
    Fixture f = new Fixture();
    when(f.operation.normalizeUsername(" Member01 ")).thenReturn("member01");
    when(f.policy.operationId(any(), any(), any())).thenReturn(mock(IdempotencyOperationId.class));
    when(f.policy.start("member01")).thenReturn(mock(IdempotencyFingerprint.class));

    assertThat(f.service.start(command()).block().status())
        .isEqualTo(StartForgotPasswordResultStatus.DUPLICATE_IN_PROGRESS);
    verify(f.policy)
        .operationId(ForgotPasswordIdempotencyAction.START, "forgot-password-start-anonymous-v1", "idem-fp-1");
    verify(f.policy).start("member01");
    assertThat(f.executor.command.ttl()).isEqualTo(Duration.ofMinutes(5));
    assertThat(f.executor.command.failureClassifier().classify(new RuntimeException("x")))
        .isEqualTo(IdempotencyFailureClassification.POSSIBLE_SIDE_EFFECT);
    verify(f.operation, never()).execute(any());
  }

  @Test
  void acquiredExecutesOnceAndCompletedReplayNeverExecutes() {
    Fixture acquired = new Fixture();
    acquired.executor.invokeProtected = true;
    when(acquired.operation.normalizeUsername(any())).thenReturn("member01");
    when(acquired.policy.operationId(any(), any(), any())).thenReturn(mock(IdempotencyOperationId.class));
    when(acquired.policy.start(any())).thenReturn(mock(IdempotencyFingerprint.class));
    var started = result(StartForgotPasswordResultStatus.STARTED);
    var response = response();
    when(acquired.operation.execute(any())).thenReturn(Mono.just(started));
    when(acquired.responses.toTransientResponse(started)).thenReturn(response);
    when(acquired.responses.fromTransientResponse(response)).thenReturn(started);
    StepVerifier.create(acquired.service.start(command())).expectNext(started).verifyComplete();
    verify(acquired.operation).execute(command());

    Fixture replay = new Fixture();
    replay.executor.outcome = IdempotencyReservationOutcome.COMPLETED_REPLAY;
    replay.executor.terminal = response;
    when(replay.operation.normalizeUsername(any())).thenReturn("member01");
    when(replay.policy.operationId(any(), any(), any())).thenReturn(mock(IdempotencyOperationId.class));
    when(replay.policy.start(any())).thenReturn(mock(IdempotencyFingerprint.class));
    when(replay.responses.fromTransientResponse(response)).thenReturn(started);
    StepVerifier.create(replay.service.start(command())).expectNext(started).verifyComplete();
    verify(replay.operation, never()).execute(any());
  }

  @Test
  void mapsEveryExistingLifecycleOutcomeWithoutProtectedExecution() {
    assertMapped(IdempotencyReservationOutcome.DUPLICATE_IN_PROGRESS, StartForgotPasswordResultStatus.DUPLICATE_IN_PROGRESS);
    assertMapped(IdempotencyReservationOutcome.KEY_REUSED_WITH_DIFFERENT_REQUEST, StartForgotPasswordResultStatus.IDEMPOTENCY_KEY_REUSED);
    assertMapped(IdempotencyReservationOutcome.FAILED_EXISTING, StartForgotPasswordResultStatus.FAILED_EXISTING);
    assertMapped(IdempotencyReservationOutcome.UNKNOWN_OUTCOME_EXISTING, StartForgotPasswordResultStatus.UNKNOWN_OUTCOME);
  }

  @Test
  void protectedSystemFailureCrossesExecutorBoundaryAsFailureBeforeCapture() {
    Fixture f = new Fixture();
    f.executor.invokeProtected = true;
    when(f.operation.normalizeUsername(any())).thenReturn("member01");
    when(f.policy.operationId(any(), any(), any())).thenReturn(mock(IdempotencyOperationId.class));
    when(f.policy.start(any())).thenReturn(mock(IdempotencyFingerprint.class));
    when(f.operation.execute(any()))
        .thenReturn(Mono.just(result(StartForgotPasswordResultStatus.SYSTEM_FAILED)));
    StepVerifier.create(f.service.start(command()))
        .expectErrorMessage("Forgot Password Start protected operation failed")
        .verify();
    verify(f.responses, never()).toTransientResponse(any());
  }

  private static void assertMapped(
      IdempotencyReservationOutcome outcome, StartForgotPasswordResultStatus expected) {
    Fixture f = new Fixture();
    f.executor.outcome = outcome;
    when(f.operation.normalizeUsername(any())).thenReturn("member01");
    when(f.policy.operationId(any(), any(), any())).thenReturn(mock(IdempotencyOperationId.class));
    when(f.policy.start(any())).thenReturn(mock(IdempotencyFingerprint.class));
    assertThat(f.service.start(command()).block().status()).isEqualTo(expected);
    verify(f.operation, never()).execute(any());
  }

  private static StartForgotPasswordCommand command() {
    return new StartForgotPasswordCommand(
        new ForgotPasswordIdempotencyKey("idem-fp-1"),
        " Member01 ",
        null,
        "en",
        "req-1",
        Duration.ofMinutes(5));
  }

  private static StartForgotPasswordResult result(StartForgotPasswordResultStatus status) {
    return new StartForgotPasswordResult(
        status,
        status == StartForgotPasswordResultStatus.STARTED ? WorkflowNextStep.ENTER_ID : WorkflowNextStep.NONE,
        null,
        null,
        null,
        List.of(),
        null);
  }

  private static HydratedIdempotentResponse response() {
    return new HydratedIdempotentResponse(200, "application/json", "{}", Map.of());
  }

  private static final class CapturingExecutor implements IdempotencyExecutor {
    private IdempotentExecutionCommand command;
    private IdempotencyReservationOutcome outcome = IdempotencyReservationOutcome.DUPLICATE_IN_PROGRESS;
    private HydratedIdempotentResponse terminal;
    private boolean invokeProtected;

    @Override
    public Mono<IdempotentExecutionResult> execute(IdempotentExecutionCommand supplied) {
      command = supplied;
      if (invokeProtected) {
        return supplied.protectedOperation().get()
            .map(response -> new IdempotentExecutionResult(IdempotencyReservationOutcome.ACQUIRED, response));
      }
      return Mono.just(new IdempotentExecutionResult(outcome, terminal));
    }
  }

  private static final class Fixture {
    private final CapturingExecutor executor = new CapturingExecutor();
    private final ForgotPasswordStartProtectedOperation operation = mock(ForgotPasswordStartProtectedOperation.class);
    private final ForgotPasswordStartExecutionBridge responses = mock(ForgotPasswordStartExecutionBridge.class);
    private final ForgotPasswordIdempotencyIntegrationPolicy policy = mock(ForgotPasswordIdempotencyIntegrationPolicy.class);
    private final IdempotentStartForgotPasswordService service =
        new IdempotentStartForgotPasswordService(executor, operation, responses, policy);
  }
}
