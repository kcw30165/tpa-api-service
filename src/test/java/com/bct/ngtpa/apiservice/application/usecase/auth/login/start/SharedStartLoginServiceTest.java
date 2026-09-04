package com.bct.ngtpa.apiservice.application.usecase.auth.login.start;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.auth.login.CredentialAuthenticationCommand;
import com.bct.ngtpa.apiservice.application.idempotency.HydratedIdempotentResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyExecutor;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyFailureClassification;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyFingerprint;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyOperationId;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyReservationOutcome;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentExecutionCommand;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentExecutionResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency.shared.LoginSharedFingerprintFactory;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency.shared.LoginSharedIdempotencyAction;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency.shared.LoginSharedIdempotencyIdentityFactory;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency.shared.LoginStartSharedExecutionBridge;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.preauth.LoginCredentialPolicyService;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.replay.LoginStartProtectedReplayCommitter;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class SharedStartLoginServiceTest {
  private static final Duration TTL = Duration.ofSeconds(300);

  @Test
  void validationFailureStopsBeforeSharedExecutorAndProtectedOperation() {
    Fixture fixture = new Fixture();
    when(fixture.policy.validateAndNormalize(any()))
        .thenThrow(new IllegalArgumentException("invalid credentials"));

    assertThatThrownBy(() -> fixture.service.start(command()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("invalid credentials");

    assertThat(fixture.executor.calls).isZero();
    verifyNoInteractions(fixture.operation, fixture.committer);
  }

  @Test
  void commandUsesNormalizedCredentialsExactPasswordAnonymousScopeAndPossibleSideEffect() {
    Fixture fixture = new Fixture();
    fixture.executor.outcome = IdempotencyReservationOutcome.DUPLICATE_IN_PROGRESS;
    fixture.executor.terminal = null;

    StepVerifier.create(fixture.service.start(command()))
        .assertNext(
            result -> assertThat(result.status()).isEqualTo(StartLoginStatus.DUPLICATE_IN_PROGRESS))
        .verifyComplete();

    verify(fixture.identities)
        .create(LoginSharedIdempotencyAction.CREDENTIALS, "login-start-anonymous-v1", "idem-key");
    verify(fixture.fingerprints).credentials("member.abc", " Exact Password ");
    assertThat(fixture.executor.command.failureClassifier().classify(new RuntimeException("x")))
        .isEqualTo(IdempotencyFailureClassification.POSSIBLE_SIDE_EFFECT);
    verifyNoInteractions(fixture.operation, fixture.committer);
  }

  @Test
  void acquiredStartedCommitsProtectedReplayBeforeTransientConversion() {
    Fixture fixture = new Fixture();
    StartLoginResult started = started(null);
    StartLoginResult committed = started("rpl_opaque");
    HydratedIdempotentResponse response = response("started");
    fixture.executor.invokeProtected = true;
    when(fixture.operation.execute(fixture.normalized)).thenReturn(Mono.just(started));
    when(fixture.committer.commit(started, TTL)).thenReturn(Mono.just(committed));
    when(fixture.responses.toTransientResponse(committed)).thenReturn(response);
    when(fixture.responses.fromTransientResponse(response)).thenReturn(committed);

    StepVerifier.create(fixture.service.start(command())).expectNext(committed).verifyComplete();

    var order = inOrder(fixture.operation, fixture.committer, fixture.responses);
    order.verify(fixture.operation).execute(fixture.normalized);
    order.verify(fixture.committer).commit(started, TTL);
    order.verify(fixture.responses).toTransientResponse(committed);
  }

  @Test
  void acquiredDeterministicRejectionSkipsProtectedReplayCommit() {
    Fixture fixture = new Fixture();
    StartLoginResult rejected = simple(StartLoginStatus.INCORRECT_PASSWORD);
    HydratedIdempotentResponse response = response("rejected");
    fixture.executor.invokeProtected = true;
    when(fixture.operation.execute(fixture.normalized)).thenReturn(Mono.just(rejected));
    when(fixture.responses.toTransientResponse(rejected)).thenReturn(response);
    when(fixture.responses.fromTransientResponse(response)).thenReturn(rejected);

    StepVerifier.create(fixture.service.start(command())).expectNext(rejected).verifyComplete();

    verify(fixture.operation).execute(fixture.normalized);
    verify(fixture.committer, never()).commit(any(), any());
    verify(fixture.responses).toTransientResponse(rejected);
  }

  @Test
  void acquiredUnavailableFailsBeforeCaptureAndCommit() {
    Fixture fixture = new Fixture();
    fixture.executor.invokeProtected = true;
    when(fixture.operation.execute(fixture.normalized))
        .thenReturn(Mono.just(simple(StartLoginStatus.UNAVAILABLE)));

    StepVerifier.create(fixture.service.start(command()))
        .expectErrorMessage("Login Start credential result is unavailable")
        .verify();

    verify(fixture.committer, never()).commit(any(), any());
    verify(fixture.responses, never()).toTransientResponse(any());
  }

  @Test
  void completedReplayAndExistingLifecycleOutcomesMapWithoutProtectedExecution() {
    Fixture fixture = new Fixture();
    HydratedIdempotentResponse replay = response("replay");
    StartLoginResult hydrated = started("rpl_existing");
    fixture.executor.outcome = IdempotencyReservationOutcome.COMPLETED_REPLAY;
    fixture.executor.terminal = replay;
    when(fixture.responses.fromTransientResponse(replay)).thenReturn(hydrated);
    StepVerifier.create(fixture.service.start(command())).expectNext(hydrated).verifyComplete();
    verifyNoInteractions(fixture.operation, fixture.committer);

    assertMapped(
        fixture,
        IdempotencyReservationOutcome.DUPLICATE_IN_PROGRESS,
        StartLoginStatus.DUPLICATE_IN_PROGRESS);
    assertMapped(
        fixture,
        IdempotencyReservationOutcome.KEY_REUSED_WITH_DIFFERENT_REQUEST,
        StartLoginStatus.KEY_REUSED);
    assertMapped(
        fixture, IdempotencyReservationOutcome.FAILED_EXISTING, StartLoginStatus.FAILED_EXISTING);
    assertMapped(
        fixture,
        IdempotencyReservationOutcome.UNKNOWN_OUTCOME_EXISTING,
        StartLoginStatus.UNKNOWN_OUTCOME);
  }

  @Test
  void protectedReplayCommitFailurePropagatesThroughExecutorBoundary() {
    Fixture fixture = new Fixture();
    StartLoginResult started = started(null);
    fixture.executor.invokeProtected = true;
    when(fixture.operation.execute(fixture.normalized)).thenReturn(Mono.just(started));
    when(fixture.committer.commit(started, TTL))
        .thenReturn(Mono.error(new IllegalStateException("commit failure")));

    StepVerifier.create(fixture.service.start(command()))
        .expectErrorMessage("commit failure")
        .verify();
    verify(fixture.responses, never()).toTransientResponse(any());
  }

  @Test
  void productionCompositionSelectsSharedStartLoginService() throws Exception {
    String config =
        java.nio.file.Files.readString(
            java.nio.file.Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/config/auth/login/LoginApplicationConfig.java"));
    assertThat(config)
        .contains("return new SharedStartLoginService(")
        .doesNotContain("return new StartLoginService(");
  }

  private void assertMapped(
      Fixture fixture, IdempotencyReservationOutcome outcome, StartLoginStatus expected) {
    fixture.executor.outcome = outcome;
    fixture.executor.terminal = null;
    StepVerifier.create(fixture.service.start(command()))
        .assertNext(result -> assertThat(result.status()).isEqualTo(expected))
        .verifyComplete();
  }

  private static StartLoginCommand command() {
    return new StartLoginCommand(" Member.ABC ", " Exact Password ", "en", "corr-1", "idem-key");
  }

  private static StartLoginResult started(String replayReference) {
    return new StartLoginResult(
        StartLoginStatus.STARTED,
        "afs-1",
        "csrf-1",
        900,
        List.of(new LoginDestinationChoice("dst-1", "email", "m***@example.com")),
        0,
        replayReference);
  }

  private static StartLoginResult simple(StartLoginStatus status) {
    return new StartLoginResult(status, null, null, null, List.of(), null, null);
  }

  private static HydratedIdempotentResponse response(String body) {
    return new HydratedIdempotentResponse(200, "application/json", body, Map.of());
  }

  private static final class CapturingExecutor implements IdempotencyExecutor {
    private int calls;
    private IdempotentExecutionCommand command;
    private IdempotencyReservationOutcome outcome =
        IdempotencyReservationOutcome.DUPLICATE_IN_PROGRESS;
    private HydratedIdempotentResponse terminal;
    private boolean invokeProtected;

    @Override
    public Mono<IdempotentExecutionResult> execute(IdempotentExecutionCommand supplied) {
      calls++;
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
    private final LoginCredentialPolicyService policy = mock(LoginCredentialPolicyService.class);
    private final CapturingExecutor executor = new CapturingExecutor();
    private final LoginStartProtectedOperation operation = mock(LoginStartProtectedOperation.class);
    private final LoginStartProtectedReplayCommitter committer =
        mock(LoginStartProtectedReplayCommitter.class);
    private final LoginStartSharedExecutionBridge responses =
        mock(LoginStartSharedExecutionBridge.class);
    private final LoginSharedIdempotencyIdentityFactory identities =
        mock(LoginSharedIdempotencyIdentityFactory.class);
    private final LoginSharedFingerprintFactory fingerprints =
        mock(LoginSharedFingerprintFactory.class);
    private final CredentialAuthenticationCommand normalized =
        new CredentialAuthenticationCommand(
            "member.abc", " Exact Password ", "en", "corr-1", ActorUserRole.ROLE_USER_MBR);
    private final SharedStartLoginService service;

    private Fixture() {
      when(policy.validateAndNormalize(any())).thenReturn(normalized);
      when(identities.create(any(), any(), any())).thenReturn(mock(IdempotencyOperationId.class));
      when(fingerprints.credentials(any(), any())).thenReturn(mock(IdempotencyFingerprint.class));
      service =
          new SharedStartLoginService(
              policy, executor, operation, committer, responses, identities, fingerprints, TTL);
    }
  }
}
