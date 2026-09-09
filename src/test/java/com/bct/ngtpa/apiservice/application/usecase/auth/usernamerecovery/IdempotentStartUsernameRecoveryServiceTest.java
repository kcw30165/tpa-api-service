package com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.bct.ngtpa.apiservice.application.dto.auth.MemberIdentityType;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryStartCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryStartResult;
import com.bct.ngtpa.apiservice.application.idempotency.HydratedIdempotentResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyExecutor;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyFailureClassification;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyFingerprint;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyOperationId;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyReservationOutcome;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentExecutionCommand;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentExecutionResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency.UsernameRecoveryIdempotencyAction;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency.UsernameRecoveryIdempotencyIntegrationPolicy;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency.start.UsernameRecoveryStartExecutionBridge;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class IdempotentStartUsernameRecoveryServiceTest {
  private static final Duration TTL = Duration.ofMinutes(10);

  @Test
  void invalidCommandStopsBeforePolicyAndExecutor() {
    Fixture f = new Fixture();
    var invalid =
        new UsernameRecoveryStartCommand(
            MemberIdentityType.STAFFNO,
            "3030609",
            "04/07/1978",
            "invalid-email",
            null,
            "cap_f67f65980e944119b8b81c74d9a1834a",
            Locale.ENGLISH,
            "abcdefgh");
    StepVerifier.create(f.service.start(invalid))
        .expectErrorSatisfies(
            e -> assertReason(e, UsernameRecoveryStartException.Reason.VALIDATION_FAILED))
        .verify();
    verifyNoInteractions(f.policy, f.operation, f.responses);
    assertThat(f.executor.command).isNull();
  }

  @Test
  void invalidIdempotencyKeyStopsBeforeSharedReservation() {
    Fixture f = new Fixture();
    var invalid = command("short");
    StepVerifier.create(f.service.start(invalid))
        .expectErrorSatisfies(
            e -> assertReason(e, UsernameRecoveryStartException.Reason.VALIDATION_FAILED))
        .verify();
    verifyNoInteractions(f.policy, f.operation, f.responses);
    assertThat(f.executor.command).isNull();
  }

  @Test
  void commandUsesExactScopeFingerprintFieldsTtlAndConservativeClassifier() {
    Fixture f = new Fixture();
    when(f.policy.operationId(any(), any(), any())).thenReturn(mock(IdempotencyOperationId.class));
    when(f.policy.start(any())).thenReturn(mock(IdempotencyFingerprint.class));
    when(f.policy.possibleSideEffect())
        .thenReturn(error -> IdempotencyFailureClassification.POSSIBLE_SIDE_EFFECT);
    StepVerifier.create(f.service.start(command("abcdefgh")))
        .expectErrorSatisfies(
            e -> assertReason(e, UsernameRecoveryStartException.Reason.IDEMPOTENCY_IN_PROGRESS))
        .verify();
    verify(f.policy)
        .operationId(
            UsernameRecoveryIdempotencyAction.START,
            "username-recovery-start-anonymous-v1",
            "abcdefgh");
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<String>> fields = ArgumentCaptor.forClass(List.class);
    verify(f.policy).start(fields.capture());
    assertThat(fields.getValue())
        .containsExactly(
            "STAFFNO",
            "3030609",
            "04/07/1978",
            "member@example.com",
            "",
            "cap_f67f65980e944119b8b81c74d9a1834a",
            "en");
    assertThat(f.executor.command.ttl()).isEqualTo(TTL);
    assertThat(f.executor.command.failureClassifier().classify(new RuntimeException("x")))
        .isEqualTo(IdempotencyFailureClassification.POSSIBLE_SIDE_EFFECT);
    verify(f.operation, never()).execute(any(), any());
  }

  @Test
  void acquiredExecutesOnceAndReturnsOriginalTypedResult() {
    Fixture f = new Fixture();
    f.executor.invokeProtected = true;
    stubPolicy(f);
    var result = memberNotFound();
    var response = response();
    when(f.operation.execute(any(), any())).thenReturn(Mono.just(result));
    when(f.responses.toTransientResponse(result, 200)).thenReturn(response);
    when(f.responses.fromTransientResponse(response)).thenReturn(result);
    StepVerifier.create(f.service.start(command("abcdefgh"))).expectNext(result).verifyComplete();
    verify(f.operation).execute(eq(command("abcdefgh")), any());
    verify(f.responses).toTransientResponse(result, 200);
    verify(f.responses).fromTransientResponse(response);
  }

  @Test
  void completedReplayNeverExecutesProtectedOperation() {
    Fixture f = new Fixture();
    stubPolicy(f);
    var result = memberNotFound();
    var response = response();
    f.executor.outcome = IdempotencyReservationOutcome.COMPLETED_REPLAY;
    f.executor.terminal = response;
    when(f.responses.fromTransientResponse(response)).thenReturn(result);
    StepVerifier.create(f.service.start(command("abcdefgh"))).expectNext(result).verifyComplete();
    verify(f.operation, never()).execute(any(), any());
    verify(f.responses).fromTransientResponse(response);
  }

  @Test
  void mapsEveryNonTerminalLifecycleOutcomeWithoutProtectedExecution() {
    assertMapped(
        IdempotencyReservationOutcome.DUPLICATE_IN_PROGRESS,
        UsernameRecoveryStartException.Reason.IDEMPOTENCY_IN_PROGRESS);
    assertMapped(
        IdempotencyReservationOutcome.KEY_REUSED_WITH_DIFFERENT_REQUEST,
        UsernameRecoveryStartException.Reason.IDEMPOTENCY_KEY_REUSED);
    assertMapped(
        IdempotencyReservationOutcome.FAILED_EXISTING,
        UsernameRecoveryStartException.Reason.SYSTEM_FAILURE);
    assertMapped(
        IdempotencyReservationOutcome.UNKNOWN_OUTCOME_EXISTING,
        UsernameRecoveryStartException.Reason.UNKNOWN_OUTCOME_EXISTING);
  }

  @Test
  void protectedFailureCrossesExecutorBoundaryOnce() {
    Fixture f = new Fixture();
    f.executor.invokeProtected = true;
    stubPolicy(f);
    when(f.operation.execute(any(), any()))
        .thenReturn(Mono.error(new IllegalStateException("failed")));
    StepVerifier.create(f.service.start(command("abcdefgh")))
        .expectErrorSatisfies(
            e -> assertReason(e, UsernameRecoveryStartException.Reason.SYSTEM_FAILURE))
        .verify();
    verify(f.operation).execute(any(), any());
    verifyNoInteractions(f.responses);
  }

  private static void assertMapped(
      IdempotencyReservationOutcome outcome, UsernameRecoveryStartException.Reason reason) {
    Fixture f = new Fixture();
    stubPolicy(f);
    f.executor.outcome = outcome;
    StepVerifier.create(f.service.start(command("abcdefgh")))
        .expectErrorSatisfies(e -> assertReason(e, reason))
        .verify();
    verify(f.operation, never()).execute(any(), any());
  }

  private static void stubPolicy(Fixture f) {
    when(f.policy.operationId(any(), any(), any())).thenReturn(mock(IdempotencyOperationId.class));
    when(f.policy.start(any())).thenReturn(mock(IdempotencyFingerprint.class));
    when(f.policy.possibleSideEffect())
        .thenReturn(error -> IdempotencyFailureClassification.POSSIBLE_SIDE_EFFECT);
  }

  private static UsernameRecoveryStartCommand command(String key) {
    return new UsernameRecoveryStartCommand(
        MemberIdentityType.STAFFNO,
        "3030609",
        "04/07/1978",
        "member@example.com",
        null,
        "cap_f67f65980e944119b8b81c74d9a1834a",
        Locale.ENGLISH,
        key);
  }

  private static UsernameRecoveryStartResult memberNotFound() {
    return new UsernameRecoveryStartResult(
        UsernameRecoveryStartResult.Outcome.MEMBER_NOT_FOUND, null, null, List.of());
  }

  private static HydratedIdempotentResponse response() {
    return new HydratedIdempotentResponse(200, "application/json", "{}", Map.of());
  }

  private static void assertReason(Throwable error, UsernameRecoveryStartException.Reason reason) {
    assertThat(error).isInstanceOf(UsernameRecoveryStartException.class);
    assertThat(((UsernameRecoveryStartException) error).reason()).isEqualTo(reason);
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
