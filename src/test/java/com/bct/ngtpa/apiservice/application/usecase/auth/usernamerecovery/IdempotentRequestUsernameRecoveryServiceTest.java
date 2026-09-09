package com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryRequestCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryRequestResult;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.idempotency.*;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency.UsernameRecoveryIdempotencyAction;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency.UsernameRecoveryIdempotencyIntegrationPolicy;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency.request.UsernameRecoveryRequestExecutionBridge;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySchemeRef;
import java.time.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class IdempotentRequestUsernameRecoveryServiceTest {
  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
  private static final Duration TTL = Duration.ofMinutes(10);

  @Test
  void commandUsesExactRequestIdentityFingerprintTtlAndAcquiredExecution() {
    Fixture f = new Fixture();
    f.executor.invokeProtected = true;
    stubPolicy(f);
    var protectedResult = UsernameRecoveryRequestProtectedExecutionResult.completed(simple());
    var response = response();
    when(f.operation.execute(any(), any(), any(), any(), any(), eq(NOW)))
        .thenReturn(Mono.just(protectedResult));
    when(f.bridge.toTransientResponse(simple(), 200)).thenReturn(response);
    when(f.bridge.fromTransientResponse(response)).thenReturn(simple());
    StepVerifier.create(f.service.request(command(), "csrf", null, null))
        .expectNext(simple())
        .verifyComplete();
    verify(f.policy)
        .operationId(UsernameRecoveryIdempotencyAction.REQUEST, "afs_1", "idem_12345678");
    verify(f.policy).request(java.util.List.of("afs_1", "sch_1"));
    assertThat(f.executor.command.ttl()).isEqualTo(TTL);
    verify(f.operation, times(1))
        .execute(eq(command()), eq("csrf"), isNull(), isNull(), startsWith("op_"), eq(NOW));
  }

  @Test
  void completedReplayDoesNotExecuteProtectedOperation() {
    Fixture f = new Fixture();
    stubPolicy(f);
    f.executor.outcome = IdempotencyReservationOutcome.COMPLETED_REPLAY;
    f.executor.terminal = response();
    when(f.bridge.fromTransientResponse(response())).thenReturn(simple());
    StepVerifier.create(f.service.request(command(), "csrf", null, null))
        .expectNext(simple())
        .verifyComplete();
    verifyNoInteractions(f.operation);
  }

  @Test
  void typedFailureBoundarySelectsExactSharedClassification() {
    Fixture f = new Fixture();
    f.executor.invokeProtected = true;
    f.executor.classifyFailure = true;
    stubPolicy(f);
    when(f.operation.execute(any(), any(), any(), any(), any(), any()))
        .thenReturn(
            Mono.just(
                UsernameRecoveryRequestProtectedExecutionResult.provenPreDispatch(
                    UsernameRecoveryRequestException.sessionAbsent())));
    StepVerifier.create(f.service.request(command(), "csrf", null, null))
        .expectErrorSatisfies(
            e -> assertThat(e).isInstanceOf(UsernameRecoveryRequestException.class))
        .verify();
    assertThat(f.executor.classification)
        .isEqualTo(IdempotencyFailureClassification.PROVEN_NO_SIDE_EFFECT);
  }

  @Test
  void everyNonTerminalLifecycleMapsWithoutProtectedExecution() {
    assertReason(
        IdempotencyReservationOutcome.DUPLICATE_IN_PROGRESS,
        UsernameRecoveryRequestException.Reason.IDEMPOTENCY_IN_PROGRESS);
    assertReason(
        IdempotencyReservationOutcome.KEY_REUSED_WITH_DIFFERENT_REQUEST,
        UsernameRecoveryRequestException.Reason.IDEMPOTENCY_KEY_REUSED);
    assertReason(
        IdempotencyReservationOutcome.FAILED_EXISTING,
        UsernameRecoveryRequestException.Reason.SYSTEM_FAILURE);
    assertReason(
        IdempotencyReservationOutcome.UNKNOWN_OUTCOME_EXISTING,
        UsernameRecoveryRequestException.Reason.UNKNOWN_OUTCOME_EXISTING);
  }

  private static void assertReason(
      IdempotencyReservationOutcome outcome, UsernameRecoveryRequestException.Reason reason) {
    Fixture f = new Fixture();
    stubPolicy(f);
    f.executor.outcome = outcome;
    StepVerifier.create(f.service.request(command(), "csrf", null, null))
        .expectErrorSatisfies(
            e -> {
              assertThat(e).isInstanceOf(UsernameRecoveryRequestException.class);
              assertThat(((UsernameRecoveryRequestException) e).reason()).isEqualTo(reason);
            })
        .verify();
    verifyNoInteractions(f.operation);
  }

  private static void stubPolicy(Fixture f) {
    when(f.policy.operationId(any(), any(), any())).thenReturn(mock(IdempotencyOperationId.class));
    when(f.policy.request(any())).thenReturn(mock(IdempotencyFingerprint.class));
    when(f.policy.provenPreDispatch())
        .thenReturn(e -> IdempotencyFailureClassification.PROVEN_NO_SIDE_EFFECT);
    when(f.policy.possibleSideEffect())
        .thenReturn(e -> IdempotencyFailureClassification.POSSIBLE_SIDE_EFFECT);
  }

  private static UsernameRecoveryRequestCommand command() {
    return new UsernameRecoveryRequestCommand(
        new AuthFlowSessionId("afs_1"), new UsernameRecoverySchemeRef("sch_1"), "idem_12345678");
  }

  private static UsernameRecoveryRequestResult simple() {
    return new UsernameRecoveryRequestResult(
        UsernameRecoveryRequestResult.Outcome.SYSTEM_FAILED, null, null, null, null);
  }

  private static HydratedIdempotentResponse response() {
    return new HydratedIdempotentResponse(200, "application/json", "{}", Map.of());
  }

  private static final class CapturingExecutor implements IdempotencyExecutor {
    IdempotentExecutionCommand command;
    boolean invokeProtected;
    boolean classifyFailure;
    IdempotencyFailureClassification classification;
    IdempotencyReservationOutcome outcome = IdempotencyReservationOutcome.DUPLICATE_IN_PROGRESS;
    HydratedIdempotentResponse terminal;

    public Mono<IdempotentExecutionResult> execute(IdempotentExecutionCommand c) {
      command = c;
      if (!invokeProtected) return Mono.just(new IdempotentExecutionResult(outcome, terminal));
      return c.protectedOperation()
          .get()
          .map(r -> new IdempotentExecutionResult(IdempotencyReservationOutcome.ACQUIRED, r))
          .onErrorResume(
              e -> {
                if (classifyFailure) classification = c.failureClassifier().classify(e);
                return Mono.error(e);
              });
    }
  }

  private static final class Fixture {
    final CapturingExecutor executor = new CapturingExecutor();
    final UsernameRecoveryRequestProtectedOperation operation =
        mock(UsernameRecoveryRequestProtectedOperation.class);
    final UsernameRecoveryRequestExecutionBridge bridge =
        mock(UsernameRecoveryRequestExecutionBridge.class);
    final UsernameRecoveryIdempotencyIntegrationPolicy policy =
        mock(UsernameRecoveryIdempotencyIntegrationPolicy.class);
    final IdempotentRequestUsernameRecoveryService service =
        new IdempotentRequestUsernameRecoveryService(
            executor, operation, bridge, policy, Clock.fixed(NOW, ZoneOffset.UTC), TTL);
  }
}
