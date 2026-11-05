package com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoverySchemeProjection;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryStartResult;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.idempotency.HydratedIdempotentResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyExecutor;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyFailureClassification;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyFingerprint;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyOperationId;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyReservationOutcome;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentExecutionCommand;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentExecutionResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency.UsernameRecoveryIdempotencyIntegrationPolicy;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency.start.UsernameRecoveryStartExecutionBridge;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySchemeRef;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * An all-unregistered SESSION_CREATED result goes through the existing session-backed shared
 * idempotency path: acquired execution runs the protected operation once, and a completed replay
 * never runs it again (so captcha consumption, lookup and session creation are not repeated).
 */
class IdempotentStartUsernameRecoveryServiceAllUnregisteredReplayTest {
  private static final Duration TTL = Duration.ofMinutes(10);

  @Test
  void acquiredExecutionReturnsTheAllUnregisteredSuccessAfterOneProtectedExecution() {
    Fixture fixture = new Fixture();
    fixture.executor.invokeProtected = true;
    stubPolicy(fixture);
    var result = allUnregisteredResult();
    var response = response();
    when(fixture.operation.execute(any(), any())).thenReturn(Mono.just(result));
    when(fixture.responses.toTransientResponse(result, 200)).thenReturn(response);
    when(fixture.responses.fromTransientResponse(response)).thenReturn(result);

    StepVerifier.create(
            fixture.service.start(UsernameRecoveryStartOperationHarness.command()))
        .expectNext(result)
        .verifyComplete();

    verify(fixture.operation).execute(any(), any());
  }

  @Test
  void completedReplayOfAnAllUnregisteredSuccessNeverRunsTheProtectedOperationAgain() {
    Fixture fixture = new Fixture();
    stubPolicy(fixture);
    var result = allUnregisteredResult();
    var response = response();
    fixture.executor.outcome = IdempotencyReservationOutcome.COMPLETED_REPLAY;
    fixture.executor.terminal = response;
    when(fixture.responses.fromTransientResponse(response)).thenReturn(result);

    StepVerifier.create(
            fixture.service.start(UsernameRecoveryStartOperationHarness.command()))
        .expectNext(result)
        .verifyComplete();

    verify(fixture.operation, never()).execute(any(), any());
    verify(fixture.responses).fromTransientResponse(response);
  }

  private static UsernameRecoveryStartResult allUnregisteredResult() {
    return new UsernameRecoveryStartResult(
        UsernameRecoveryStartResult.Outcome.SESSION_CREATED,
        new AuthFlowSessionId("afs_replay-1"),
        "csrf-replay-value",
        600,
        List.of(
            new UsernameRecoverySchemeProjection(
                new UsernameRecoverySchemeRef("sch_replay-1"), "OI", "JPM", false),
            new UsernameRecoverySchemeProjection(
                new UsernameRecoverySchemeRef("sch_replay-2"), "DB", "DBA", false)));
  }

  private static HydratedIdempotentResponse response() {
    return new HydratedIdempotentResponse(200, "application/json", "{}", Map.of());
  }

  private static void stubPolicy(Fixture fixture) {
    when(fixture.policy.operationId(any(), any(), any()))
        .thenReturn(mock(IdempotencyOperationId.class));
    when(fixture.policy.start(any())).thenReturn(mock(IdempotencyFingerprint.class));
    when(fixture.policy.possibleSideEffect())
        .thenReturn(error -> IdempotencyFailureClassification.POSSIBLE_SIDE_EFFECT);
  }

  private static final class CapturingExecutor implements IdempotencyExecutor {
    private IdempotencyReservationOutcome outcome =
        IdempotencyReservationOutcome.DUPLICATE_IN_PROGRESS;
    private HydratedIdempotentResponse terminal;
    private boolean invokeProtected;

    @Override
    public Mono<IdempotentExecutionResult> execute(IdempotentExecutionCommand supplied) {
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
    private final UsernameRecoveryStartProtectedOperation operation =
        mock(UsernameRecoveryStartProtectedOperation.class);
    private final UsernameRecoveryStartExecutionBridge responses =
        mock(UsernameRecoveryStartExecutionBridge.class);
    private final UsernameRecoveryIdempotencyIntegrationPolicy policy =
        mock(UsernameRecoveryIdempotencyIntegrationPolicy.class);
    private final IdempotentStartUsernameRecoveryService service =
        new IdempotentStartUsernameRecoveryService(
            executor, operation, responses, policy, new UsernameRecoveryStartValidator(), TTL);
  }
}
