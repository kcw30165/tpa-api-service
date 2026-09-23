package com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryCancelCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryCancelResult;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.idempotency.HydratedIdempotentResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyExecutor;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyFingerprint;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyOperationId;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyReservationOutcome;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentExecutionCommand;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentExecutionResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency.UsernameRecoveryIdempotencyIntegrationPolicy;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency.cancel.UsernameRecoveryCancelExecutionBridge;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Branch-oriented reactive tests for {@link IdempotentCancelUsernameRecoveryService}. Mirrors the
 * proven ArgumentCaptor + direct-supplier-invocation technique from
 * IdempotentCompleteForgotPasswordServiceTest (round 58/94), confirmed applicable to this sibling
 * (round 97).
 */
class IdempotentCancelUsernameRecoveryServiceTest {

  private static final Instant NOW = Instant.parse("2026-09-14T00:00:00Z");
  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
  private final Duration ttl = Duration.ofMinutes(5);

  private final IdempotencyExecutor executor = mock(IdempotencyExecutor.class);
  private final UsernameRecoveryCancelProtectedOperation operation =
      mock(UsernameRecoveryCancelProtectedOperation.class);
  private final UsernameRecoveryCancelExecutionBridge bridge =
      mock(UsernameRecoveryCancelExecutionBridge.class);
  private final UsernameRecoveryIdempotencyIntegrationPolicy policy =
      mock(UsernameRecoveryIdempotencyIntegrationPolicy.class);

  private final AuthFlowSessionId sid = new AuthFlowSessionId("afs-idem-cancel-1");

  @BeforeEach
  void stubPolicyBuildsOperationIdentity() {
    when(policy.operationId(any(), any(), any())).thenReturn(mock(IdempotencyOperationId.class));
    when(policy.cancel(any())).thenReturn(mock(IdempotencyFingerprint.class));
  }

  private IdempotentCancelUsernameRecoveryService newService() {
    return new IdempotentCancelUsernameRecoveryService(
        executor, operation, bridge, policy, clock, ttl);
  }

  @Test
  void nonPositiveTtlThrows() {
    assertThatThrownBy(
            () ->
                new IdempotentCancelUsernameRecoveryService(
                    executor, operation, bridge, policy, clock, Duration.ZERO))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private UsernameRecoveryCancelCommand command() {
    return new UsernameRecoveryCancelCommand(sid, "idem-key-1");
  }

  @Test
  void nullCommandErrors() {
    StepVerifier.create(newService().cancel(null, "csrf", "https://origin", "https://ref"))
        .expectError(UsernameRecoveryCancelException.class)
        .verify();
  }

  @Test
  void tooShortIdempotencyKeyErrors() {
    UsernameRecoveryCancelCommand shortKey = new UsernameRecoveryCancelCommand(sid, "short");
    StepVerifier.create(newService().cancel(shortKey, "csrf", "https://origin", "https://ref"))
        .expectError(UsernameRecoveryCancelException.class)
        .verify();
  }

  /** Captures the command passed to executor.execute(...) and returns it. */
  private IdempotentExecutionCommand captureExecutionCommand() {
    ArgumentCaptor<IdempotentExecutionCommand> captor =
        ArgumentCaptor.forClass(IdempotentExecutionCommand.class);
    when(executor.execute(captor.capture()))
        .thenReturn(
            Mono.just(
                new IdempotentExecutionResult(
                    IdempotencyReservationOutcome.ACQUIRED,
                    mock(HydratedIdempotentResponse.class))));
    newService().cancel(command(), "csrf", "https://origin", "https://ref").block();
    return captor.getValue();
  }

  // ----- direct protectedOperation supplier tests (executeAcquired) -----------

  @Test
  void protectedOperationCompletedSucceeds() {
    UsernameRecoveryCancelResult result =
        new UsernameRecoveryCancelResult(UsernameRecoveryCancelResult.Outcome.SUCCESS);
    when(operation.execute(any(), any(), any(), any(), any()))
        .thenReturn(Mono.just(UsernameRecoveryCancelProtectedExecutionResult.completed(result)));
    HydratedIdempotentResponse transientResponse = mock(HydratedIdempotentResponse.class);
    when(bridge.toTransientResponse(result, 200)).thenReturn(transientResponse);
    // NOTE (round 99): captureExecutionCommand() runs the FULL cancel() flow
    // (executor stubbed to ACQUIRED), which routes through
    // bridge.fromTransientResponse(...) - must be stubbed non-null or route()
    // throws NPE inside Mono.just(null).
    when(bridge.fromTransientResponse(any())).thenReturn(result);
    IdempotentExecutionCommand cmd = captureExecutionCommand();
    StepVerifier.create(cmd.protectedOperation().get())
        .expectNext(transientResponse)
        .verifyComplete();
  }

  @Test
  void protectedOperationFailedErrors() {
    when(operation.execute(any(), any(), any(), any(), any()))
        .thenReturn(
            Mono.just(
                UsernameRecoveryCancelProtectedExecutionResult.possibleSideEffect(
                    new IllegalStateException("boom"))));
    // NOTE (round 99): captureExecutionCommand() runs the FULL cancel() flow
    // (executor stubbed to ACQUIRED); stub fromTransientResponse so THAT
    // outer routing does not itself NPE before we reach the direct supplier
    // invocation under test below.
    when(bridge.fromTransientResponse(any()))
        .thenReturn(new UsernameRecoveryCancelResult(UsernameRecoveryCancelResult.Outcome.SUCCESS));
    IdempotentExecutionCommand cmd = captureExecutionCommand();
    StepVerifier.create(cmd.protectedOperation().get()).expectError().verify();
  }

  // ----- route() outcome-switch branches (via canned executor results) --------------

  @Test
  void acquiredDelegatesToBridgeFromTransientResponse() {
    HydratedIdempotentResponse response = mock(HydratedIdempotentResponse.class);
    when(executor.execute(any()))
        .thenReturn(
            Mono.just(
                new IdempotentExecutionResult(IdempotencyReservationOutcome.ACQUIRED, response)));
    UsernameRecoveryCancelResult expected =
        new UsernameRecoveryCancelResult(UsernameRecoveryCancelResult.Outcome.SUCCESS);
    when(bridge.fromTransientResponse(response)).thenReturn(expected);
    StepVerifier.create(newService().cancel(command(), "csrf", "https://origin", "https://ref"))
        .expectNext(expected)
        .verifyComplete();
  }

  @Test
  void completedReplayDelegatesToBridgeFromTransientResponse() {
    HydratedIdempotentResponse response = mock(HydratedIdempotentResponse.class);
    when(executor.execute(any()))
        .thenReturn(
            Mono.just(
                new IdempotentExecutionResult(
                    IdempotencyReservationOutcome.COMPLETED_REPLAY, response)));
    UsernameRecoveryCancelResult expected =
        new UsernameRecoveryCancelResult(UsernameRecoveryCancelResult.Outcome.SUCCESS);
    when(bridge.fromTransientResponse(response)).thenReturn(expected);
    StepVerifier.create(newService().cancel(command(), "csrf", "https://origin", "https://ref"))
        .expectNext(expected)
        .verifyComplete();
  }

  @Test
  void duplicateInProgressErrors() {
    when(executor.execute(any()))
        .thenReturn(
            Mono.just(
                new IdempotentExecutionResult(
                    IdempotencyReservationOutcome.DUPLICATE_IN_PROGRESS, null)));
    StepVerifier.create(newService().cancel(command(), "csrf", "https://origin", "https://ref"))
        .expectError(UsernameRecoveryCancelException.class)
        .verify();
  }

  @Test
  void keyReusedErrors() {
    when(executor.execute(any()))
        .thenReturn(
            Mono.just(
                new IdempotentExecutionResult(
                    IdempotencyReservationOutcome.KEY_REUSED_WITH_DIFFERENT_REQUEST, null)));
    StepVerifier.create(newService().cancel(command(), "csrf", "https://origin", "https://ref"))
        .expectError(UsernameRecoveryCancelException.class)
        .verify();
  }

  @Test
  void failedExistingErrors() {
    when(executor.execute(any()))
        .thenReturn(
            Mono.just(
                new IdempotentExecutionResult(
                    IdempotencyReservationOutcome.FAILED_EXISTING, null)));
    StepVerifier.create(newService().cancel(command(), "csrf", "https://origin", "https://ref"))
        .expectError(UsernameRecoveryCancelException.class)
        .verify();
  }

  @Test
  void unknownOutcomeExistingErrors() {
    when(executor.execute(any()))
        .thenReturn(
            Mono.just(
                new IdempotentExecutionResult(
                    IdempotencyReservationOutcome.UNKNOWN_OUTCOME_EXISTING, null)));
    StepVerifier.create(newService().cancel(command(), "csrf", "https://origin", "https://ref"))
        .expectError(UsernameRecoveryCancelException.class)
        .verify();
  }

  @Test
  void executorErrorMapsToSystemFailure() {
    when(executor.execute(any())).thenReturn(Mono.error(new IllegalStateException("boom")));
    StepVerifier.create(newService().cancel(command(), "csrf", "https://origin", "https://ref"))
        .expectError(UsernameRecoveryCancelException.class)
        .verify();
  }
}
