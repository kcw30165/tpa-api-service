package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.complete;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowContext;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowEncryptedContext;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordProtectedPhase;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyKey;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowScenario;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionData;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionSnapshot;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowStatus;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.SessionRevision;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.idempotency.HydratedIdempotentResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyExecutor;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyFingerprint;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyOperationId;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyReservationOutcome;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentExecutionCommand;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentExecutionResult;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort;
import com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordAuthFlowContextProtectorPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyIntegrationPolicy;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.complete.ForgotPasswordCompleteExecutionBridge;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Branch-oriented reactive tests for {@link IdempotentCompleteForgotPasswordService}. Captures the
 * {@link IdempotentExecutionCommand} passed to the mocked executor and invokes its {@code
 * protectedOperation} supplier directly to exercise the private executeAcquired/executeLoaded/
 * loadContext methods (a naive canned-result mock would otherwise skip them entirely).
 *
 * <p>{@code policy} is a mocked collaborator; {@link #stubPolicyBuildsOperationIdentity()} stubs
 * {@code operationId(...)}/{@code complete()} on EVERY test (confirmed round 57: the real
 * production {@code complete()} method builds an {@link IdempotentExecutionCommand} from these two
 * policy calls BEFORE ever reaching the executor - an unstubbed mock returns null for both, which
 * {@code IdempotentExecutionCommand}'s constructor rejects).
 */
class IdempotentCompleteForgotPasswordServiceTest {

  private static final Instant NOW = Instant.parse("2026-09-14T00:00:00Z");
  private static final String CONTEXT_CIPHERTEXT_KEY = "forgotPasswordContextCiphertext";
  private static final String CONTEXT_KEY_VERSION_KEY = "forgotPasswordContextKeyVersion";
  private static final String CONTEXT_ALGORITHM_KEY = "forgotPasswordContextAlgorithm";

  private final IdempotencyExecutor executor = mock(IdempotencyExecutor.class);
  private final AuthFlowSessionPort sessions = mock(AuthFlowSessionPort.class);
  private final ForgotPasswordAuthFlowContextProtectorPort protector =
      mock(ForgotPasswordAuthFlowContextProtectorPort.class);
  private final ForgotPasswordCompleteProtectedOperation operation =
      mock(ForgotPasswordCompleteProtectedOperation.class);
  private final ForgotPasswordCompleteExecutionBridge responses =
      mock(ForgotPasswordCompleteExecutionBridge.class);
  private final ForgotPasswordIdempotencyIntegrationPolicy policy =
      mock(ForgotPasswordIdempotencyIntegrationPolicy.class);

  private final AuthFlowSessionId sid = new AuthFlowSessionId("afs-idem-complete-1");

  @BeforeEach
  void stubPolicyBuildsOperationIdentity() {
    when(policy.operationId(any(), any(), any())).thenReturn(mock(IdempotencyOperationId.class));
    when(policy.complete()).thenReturn(mock(IdempotencyFingerprint.class));
  }

  private IdempotentCompleteForgotPasswordService newService() {
    return new IdempotentCompleteForgotPasswordService(
        executor, sessions, protector, operation, responses, policy);
  }

  private CompleteForgotPasswordCommand command() {
    return new CompleteForgotPasswordCommand(
        sid,
        "csrf",
        new ForgotPasswordIdempotencyKey("idem-1"),
        "Password1!",
        "Password1!",
        "https://origin",
        "https://ref",
        "en",
        "corr-1",
        Duration.ofMinutes(5));
  }

  private AuthFlowSession sessionWith(Map<String, String> attributes) {
    return new AuthFlowSession(
        sid,
        AuthFlowScenario.FORGOT_PASSWORD,
        AuthFlowStatus.RESET_AUTHORIZED,
        "csrfhash",
        null,
        new AuthFlowSessionData("email", "j***@example.com", attributes),
        NOW.minusSeconds(120),
        NOW.minusSeconds(60),
        NOW.plusSeconds(1800));
  }

  private AuthFlowSessionSnapshot snapshotOf(AuthFlowSession session) {
    return new AuthFlowSessionSnapshot(session, new SessionRevision("0".repeat(32)));
  }

  private ForgotPasswordAuthFlowContext contextWithPhase(ForgotPasswordProtectedPhase phase) {
    return new ForgotPasswordAuthFlowContext(
        "john",
        "idv-1",
        "CUSTID",
        true,
        "subj-1",
        "email",
        "john@example.com",
        "j***@example.com",
        null,
        null,
        "v1",
        phase);
  }

  private static CompleteForgotPasswordResult successResult() {
    return new CompleteForgotPasswordResult(
        CompleteForgotPasswordStatus.SUCCESS,
        WorkflowNextStep.RETURN_TO_LOGIN,
        true,
        true,
        List.of());
  }

  private static CompleteForgotPasswordResult systemFailedResult() {
    return new CompleteForgotPasswordResult(
        CompleteForgotPasswordStatus.SYSTEM_FAILED,
        WorkflowNextStep.PASSWORD_SETUP,
        false,
        false,
        List.of());
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
    newService().complete(command()).block();
    return captor.getValue();
  }

  // ----- direct protectedOperation supplier tests (executeAcquired/executeLoaded/loadContext) --

  @Test
  void protectedOperationWithSessionMissingYieldsInvalidAuthFlowTransientResponse() {
    when(sessions.loadSnapshot(any())).thenReturn(Mono.just(Optional.empty()));
    HydratedIdempotentResponse transientResponse = mock(HydratedIdempotentResponse.class);
    when(responses.toTransientResponse(any())).thenReturn(transientResponse);
    IdempotentExecutionCommand cmd = captureExecutionCommand();
    Mono<HydratedIdempotentResponse> supplierResult = cmd.protectedOperation().get();
    StepVerifier.create(supplierResult).expectNext(transientResponse).verifyComplete();
  }

  @Test
  void protectedOperationWithMissingContextAttributesYieldsInvalidAuthFlow() {
    when(sessions.loadSnapshot(any()))
        .thenReturn(Mono.just(Optional.of(snapshotOf(sessionWith(Map.of())))));
    HydratedIdempotentResponse transientResponse = mock(HydratedIdempotentResponse.class);
    when(responses.toTransientResponse(any())).thenReturn(transientResponse);
    IdempotentExecutionCommand cmd = captureExecutionCommand();
    StepVerifier.create(cmd.protectedOperation().get())
        .expectNext(transientResponse)
        .verifyComplete();
  }

  @Test
  void protectedOperationWithValidContextInvokesOperationAndReturnsTerminal() {
    Map<String, String> attrs =
        Map.of(
            CONTEXT_CIPHERTEXT_KEY,
            "ct",
            CONTEXT_KEY_VERSION_KEY,
            "kv",
            CONTEXT_ALGORITHM_KEY,
            ForgotPasswordAuthFlowEncryptedContext.DEFAULT_ALGORITHM);
    when(sessions.loadSnapshot(any()))
        .thenReturn(Mono.just(Optional.of(snapshotOf(sessionWith(attrs)))));
    when(protector.unprotect(any()))
        .thenReturn(contextWithPhase(ForgotPasswordProtectedPhase.PASSWORD_SETUP));
    when(operation.execute(any()))
        .thenReturn(
            Mono.just(ForgotPasswordCompleteProtectedExecutionResult.terminal(successResult())));
    HydratedIdempotentResponse transientResponse = mock(HydratedIdempotentResponse.class);
    when(responses.toTransientResponse(any())).thenReturn(transientResponse);
    IdempotentExecutionCommand cmd = captureExecutionCommand();
    StepVerifier.create(cmd.protectedOperation().get())
        .expectNext(transientResponse)
        .verifyComplete();
  }

  @Test
  void protectedOperationTechnicalFailureErrors() {
    Map<String, String> attrs =
        Map.of(
            CONTEXT_CIPHERTEXT_KEY,
            "ct",
            CONTEXT_KEY_VERSION_KEY,
            "kv",
            CONTEXT_ALGORITHM_KEY,
            ForgotPasswordAuthFlowEncryptedContext.DEFAULT_ALGORITHM);
    when(sessions.loadSnapshot(any()))
        .thenReturn(Mono.just(Optional.of(snapshotOf(sessionWith(attrs)))));
    when(protector.unprotect(any()))
        .thenReturn(contextWithPhase(ForgotPasswordProtectedPhase.PASSWORD_SETUP));
    when(operation.execute(any()))
        .thenReturn(
            Mono.just(
                ForgotPasswordCompleteProtectedExecutionResult.technicalFailure(
                    systemFailedResult())));
    IdempotentExecutionCommand cmd = captureExecutionCommand();
    StepVerifier.create(cmd.protectedOperation().get())
        .expectError(IllegalStateException.class)
        .verify();
  }

  // ----- route() outcome-switch branches (via canned executor results) --------------

  @Test
  void completedReplayDelegatesToResponsesFromTransientResponse() {
    HydratedIdempotentResponse response = mock(HydratedIdempotentResponse.class);
    when(executor.execute(any()))
        .thenReturn(
            Mono.just(
                new IdempotentExecutionResult(
                    IdempotencyReservationOutcome.COMPLETED_REPLAY, response)));
    CompleteForgotPasswordResult expected = successResult();
    when(responses.fromTransientResponse(response)).thenReturn(expected);
    StepVerifier.create(newService().complete(command())).expectNext(expected).verifyComplete();
  }

  @Test
  void duplicateInProgressYieldsWait() {
    when(executor.execute(any()))
        .thenReturn(
            Mono.just(
                new IdempotentExecutionResult(
                    IdempotencyReservationOutcome.DUPLICATE_IN_PROGRESS, null)));
    StepVerifier.create(newService().complete(command()))
        .assertNext(
            r -> {
              assertThat(r.status()).isEqualTo(CompleteForgotPasswordStatus.DUPLICATE_IN_PROGRESS);
              assertThat(r.nextStep()).isEqualTo(WorkflowNextStep.WAIT);
            })
        .verifyComplete();
  }

  @Test
  void keyReusedYieldsIdempotencyKeyReused() {
    when(executor.execute(any()))
        .thenReturn(
            Mono.just(
                new IdempotentExecutionResult(
                    IdempotencyReservationOutcome.KEY_REUSED_WITH_DIFFERENT_REQUEST, null)));
    StepVerifier.create(newService().complete(command()))
        .assertNext(
            r ->
                assertThat(r.status())
                    .isEqualTo(CompleteForgotPasswordStatus.IDEMPOTENCY_KEY_REUSED))
        .verifyComplete();
  }

  @Test
  void failedExistingYieldsSystemFailed() {
    when(executor.execute(any()))
        .thenReturn(
            Mono.just(
                new IdempotentExecutionResult(
                    IdempotencyReservationOutcome.FAILED_EXISTING, null)));
    StepVerifier.create(newService().complete(command()))
        .assertNext(
            r -> {
              assertThat(r.status()).isEqualTo(CompleteForgotPasswordStatus.SYSTEM_FAILED);
              assertThat(r.nextStep()).isEqualTo(WorkflowNextStep.PASSWORD_SETUP);
            })
        .verifyComplete();
  }

  @Test
  void unknownOutcomeExistingYieldsUnknownOutcome() {
    when(executor.execute(any()))
        .thenReturn(
            Mono.just(
                new IdempotentExecutionResult(
                    IdempotencyReservationOutcome.UNKNOWN_OUTCOME_EXISTING, null)));
    StepVerifier.create(newService().complete(command()))
        .assertNext(
            r -> assertThat(r.status()).isEqualTo(CompleteForgotPasswordStatus.UNKNOWN_OUTCOME))
        .verifyComplete();
  }

  @Test
  void executorErrorYieldsSystemFailed() {
    when(executor.execute(any())).thenReturn(Mono.error(new IllegalStateException("boom")));
    StepVerifier.create(newService().complete(command()))
        .assertNext(
            r -> assertThat(r.status()).isEqualTo(CompleteForgotPasswordStatus.SYSTEM_FAILED))
        .verifyComplete();
  }
}
