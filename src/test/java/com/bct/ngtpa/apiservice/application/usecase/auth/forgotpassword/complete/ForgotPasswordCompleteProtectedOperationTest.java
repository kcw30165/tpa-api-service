package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.complete;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.ForgotPasswordResetAuthorization;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowContext;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowEncryptedContext;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordProtectedPhase;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyKey;
import com.bct.ngtpa.apiservice.application.dto.session.SessionLockHandle;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowScenario;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionData;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionMutationOutcome;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionMutationResult;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionSnapshot;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowStatus;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.SessionRevision;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorDescriptor;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorSeverity;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorType;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionMutationLockPort;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort;
import com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordAuthFlowContextProtectorPort;
import com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordCompletionPort;
import com.bct.ngtpa.apiservice.application.service.auth.forgotpassword.ForgotPasswordResetAuthorizationValidationResult;
import com.bct.ngtpa.apiservice.application.service.auth.forgotpassword.ForgotPasswordResetAuthorizationValidationStatus;
import com.bct.ngtpa.apiservice.application.service.auth.forgotpassword.ForgotPasswordResetAuthorizationValidator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Branch-oriented reactive tests for {@link ForgotPasswordCompleteProtectedOperation}. Collaborators
 * are mocked; the {@code RevisionAwareAuthFlowSessionWriter} is driven through its port contract
 * ({@code loadSnapshot} + {@code updateRevisioned}). A fixed {@link Clock} keeps timing deterministic.
 */
class ForgotPasswordCompleteProtectedOperationTest {

  private static final Instant NOW = Instant.parse("2026-09-14T00:00:00Z");
  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
  private final Duration lease = Duration.ofSeconds(30);

  private final AuthFlowSessionPort sessions = mock(AuthFlowSessionPort.class);
  private final AuthFlowSessionMutationLockPort locks = mock(AuthFlowSessionMutationLockPort.class);
  private final ForgotPasswordAuthFlowContextProtectorPort protector =
      mock(ForgotPasswordAuthFlowContextProtectorPort.class);
  private final ForgotPasswordCompletionPort completion = mock(ForgotPasswordCompletionPort.class);
  private final ForgotPasswordResetAuthorizationValidator authValidator =
      mock(ForgotPasswordResetAuthorizationValidator.class);
  private final ForgotPasswordPasswordValidator passwordValidator =
      mock(ForgotPasswordPasswordValidator.class);

  private final AuthFlowSessionId sid = new AuthFlowSessionId("afs-123");
  private final AuthFlowSession session =
      new AuthFlowSession(
          sid,
          AuthFlowScenario.FORGOT_PASSWORD,
          AuthFlowStatus.RESET_AUTHORIZED,
          "csrf-hash",
          null,
          AuthFlowSessionData.empty(),
          NOW,
          NOW,
          NOW.plusSeconds(1800));
  private final AuthFlowSessionSnapshot snapshot =
      new AuthFlowSessionSnapshot(session, new SessionRevision("0".repeat(32)));
  private final ForgotPasswordAuthFlowContext context = context();

  // ----- helpers ---------------------------------------------------------

  private ForgotPasswordCompleteProtectedOperation newOp() {
    return newOp(lease);
  }

  private ForgotPasswordCompleteProtectedOperation newOp(Duration lockLease) {
    return new ForgotPasswordCompleteProtectedOperation(
        sessions, locks, protector, completion, authValidator, passwordValidator, clock, lockLease);
  }

  private static ForgotPasswordAuthFlowContext context() {
    ForgotPasswordResetAuthorization auth =
        new ForgotPasswordResetAuthorization(
            "auth-1", "afs-123", "subj-1", NOW, NOW.plusSeconds(3600), null);
    return new ForgotPasswordAuthFlowContext(
        "john",
        "idv",
        "CUSTID",
        true,
        "subj-1",
        "EMAIL",
        "prot",
        "j***",
        auth,
        "env1",
        "v1",
        ForgotPasswordProtectedPhase.IDENTITY_VERIFIED);
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

  private ForgotPasswordCompleteProtectedOperationInput input() {
    return new ForgotPasswordCompleteProtectedOperationInput(command(), context, snapshot);
  }

  private void givenLockAcquired() {
    when(locks.acquire(any(), any()))
        .thenReturn(Mono.just(new SessionLockHandle(true, "lock-key", "lock-token")));
    when(locks.release(any())).thenReturn(Mono.just(true));
  }

  private void givenAuthValid() {
    when(authValidator.validate(any(), any(), any(), any(), any()))
        .thenReturn(
            ForgotPasswordResetAuthorizationValidationResult.of(
                ForgotPasswordResetAuthorizationValidationStatus.VALID));
  }

  private void givenPasswordValid() {
    when(passwordValidator.validate(any(), any(), any()))
        .thenReturn(ForgotPasswordPasswordValidationResult.success());
  }

  private void givenWriterApplies() {
    when(sessions.loadSnapshot(any())).thenReturn(Mono.just(Optional.of(snapshot)));
    when(sessions.updateRevisioned(any()))
        .thenReturn(
            Mono.just(
                new AuthFlowSessionMutationResult(
                    AuthFlowSessionMutationOutcome.APPLIED, snapshot)));
    when(protector.protect(any()))
        .thenReturn(new ForgotPasswordAuthFlowEncryptedContext("ct", "kv", "AES_GCM_V1"));
  }

  private static WorkflowErrorDescriptor anError() {
    return new WorkflowErrorDescriptor(
        WorkflowErrorType.FORM,
        "err.forgot-password.password.minLength",
        null,
        List.of("password"),
        WorkflowErrorSeverity.ERROR,
        "SERVER");
  }

  // ----- constructor guards ---------------------------------------------

  @Test
  void constructorRejectsNullCollaborator() {
    assertThatThrownBy(
            () ->
                new ForgotPasswordCompleteProtectedOperation(
                    null, locks, protector, completion, authValidator, passwordValidator, clock,
                    lease))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void constructorRejectsNonPositiveLease() {
    assertThatThrownBy(() -> newOp(Duration.ZERO))
        .isInstanceOf(IllegalArgumentException.class);
  }

  // ----- 1: duplicate (lock not acquired) --------------------------------

  @Test
  void lockNotAcquiredYieldsDuplicateInProgress() {
    when(locks.acquire(any(), any())).thenReturn(Mono.just(SessionLockHandle.notAcquired()));
    StepVerifier.create(newOp().execute(input()))
        .assertNext(
            r ->
                assertThat(r.result().status())
                    .isEqualTo(CompleteForgotPasswordStatus.DUPLICATE_IN_PROGRESS))
        .verifyComplete();
  }

  // ----- 2: unknown-outcome-blocked authorization ------------------------

  @Test
  void unknownOutcomeBlockedAuthorizationYieldsUnknownOutcome() {
    givenLockAcquired();
    when(authValidator.validate(any(), any(), any(), any(), any()))
        .thenReturn(
            ForgotPasswordResetAuthorizationValidationResult.of(
                ForgotPasswordResetAuthorizationValidationStatus.UNKNOWN_OUTCOME_BLOCKED));
    StepVerifier.create(newOp().execute(input()))
        .assertNext(
            r -> {
              assertThat(r.outcome())
                  .isEqualTo(ForgotPasswordCompleteProtectedExecutionResult.Outcome.UNKNOWN_OUTCOME);
              assertThat(r.result().status())
                  .isEqualTo(CompleteForgotPasswordStatus.UNKNOWN_OUTCOME);
            })
        .verifyComplete();
  }

  // ----- 3: invalid auth-flow -------------------------------------------

  @Test
  void invalidAuthorizationYieldsInvalidAuthFlow() {
    givenLockAcquired();
    when(authValidator.validate(any(), any(), any(), any(), any()))
        .thenReturn(
            ForgotPasswordResetAuthorizationValidationResult.of(
                ForgotPasswordResetAuthorizationValidationStatus.EXPIRED));
    StepVerifier.create(newOp().execute(input()))
        .assertNext(
            r ->
                assertThat(r.result().status())
                    .isEqualTo(CompleteForgotPasswordStatus.INVALID_AUTH_FLOW))
        .verifyComplete();
  }

  // ----- 4: password-validation failed ----------------------------------

  @Test
  void passwordValidationFailureYieldsValidationFailed() {
    givenLockAcquired();
    givenAuthValid();
    when(passwordValidator.validate(any(), any(), any()))
        .thenReturn(ForgotPasswordPasswordValidationResult.failure(List.of(anError())));
    StepVerifier.create(newOp().execute(input()))
        .assertNext(
            r ->
                assertThat(r.result().status())
                    .isEqualTo(CompleteForgotPasswordStatus.VALIDATION_FAILED))
        .verifyComplete();
  }

  // ----- 5: REJECTED -> password.invalid ---------------------------------

  @Test
  void rejectedPasswordInvalidYieldsBusinessRejected() {
    givenLockAcquired();
    givenAuthValid();
    givenPasswordValid();
    when(completion.complete(any()))
        .thenReturn(Mono.just(ForgotPasswordCompletionResult.rejected("err.tpa.password.invalid")));
    StepVerifier.create(newOp().execute(input()))
        .assertNext(
            r ->
                assertThat(r.result().status())
                    .isEqualTo(CompleteForgotPasswordStatus.BUSINESS_REJECTED))
        .verifyComplete();
  }

  // ----- 6: REJECTED -> password.reused ----------------------------------

  @Test
  void rejectedPasswordReusedYieldsBusinessRejected() {
    givenLockAcquired();
    givenAuthValid();
    givenPasswordValid();
    when(completion.complete(any()))
        .thenReturn(
            Mono.just(
                ForgotPasswordCompletionResult.rejected(
                    "err.tpa.password.used.in.last.5.changes.")));
    StepVerifier.create(newOp().execute(input()))
        .assertNext(
            r ->
                assertThat(r.result().status())
                    .isEqualTo(CompleteForgotPasswordStatus.BUSINESS_REJECTED))
        .verifyComplete();
  }

  // ----- 7: REJECTED -> unrecognized -> technical failure ----------------

  @Test
  void rejectedUnrecognizedCodeYieldsSystemFailed() {
    givenLockAcquired();
    givenAuthValid();
    givenPasswordValid();
    when(completion.complete(any()))
        .thenReturn(Mono.just(ForgotPasswordCompletionResult.rejected("private.downstream.detail")));
    StepVerifier.create(newOp().execute(input()))
        .assertNext(
            r -> {
              assertThat(r.outcome())
                  .isEqualTo(
                      ForgotPasswordCompleteProtectedExecutionResult.Outcome.TECHNICAL_FAILURE);
              assertThat(r.result().status())
                  .isEqualTo(CompleteForgotPasswordStatus.SYSTEM_FAILED);
            })
        .verifyComplete();
  }

  // ----- 8: completion TECHNICAL_FAILURE ---------------------------------

  @Test
  void completionTechnicalFailureYieldsSystemFailed() {
    givenLockAcquired();
    givenAuthValid();
    givenPasswordValid();
    when(completion.complete(any()))
        .thenReturn(Mono.just(ForgotPasswordCompletionResult.technicalFailure()));
    StepVerifier.create(newOp().execute(input()))
        .assertNext(
            r ->
                assertThat(r.outcome())
                    .isEqualTo(
                        ForgotPasswordCompleteProtectedExecutionResult.Outcome.TECHNICAL_FAILURE))
        .verifyComplete();
  }

  // ----- 9: RESET success ------------------------------------------------

  @Test
  void resetSuccessYieldsSuccess() {
    givenLockAcquired();
    givenAuthValid();
    givenPasswordValid();
    givenWriterApplies();
    when(completion.complete(any()))
        .thenReturn(Mono.just(ForgotPasswordCompletionResult.reset("29/07/2026", "14:30:15")));
    StepVerifier.create(newOp().execute(input()))
        .assertNext(
            r -> {
              assertThat(r.outcome())
                  .isEqualTo(ForgotPasswordCompleteProtectedExecutionResult.Outcome.TERMINAL);
              assertThat(r.result().status()).isEqualTo(CompleteForgotPasswordStatus.SUCCESS);
            })
        .verifyComplete();
  }

  // ----- 10: REJECTED -> member.not.found (session updated) --------------

  @Test
  void rejectedMemberNotFoundYieldsBusinessRejected() {
    givenLockAcquired();
    givenAuthValid();
    givenPasswordValid();
    givenWriterApplies();
    when(completion.complete(any()))
        .thenReturn(Mono.just(ForgotPasswordCompletionResult.rejected("err.tpa.member.not.found")));
    StepVerifier.create(newOp().execute(input()))
        .assertNext(
            r ->
                assertThat(r.result().status())
                    .isEqualTo(CompleteForgotPasswordStatus.BUSINESS_REJECTED))
        .verifyComplete();
  }

  // ----- 11: completion UNKNOWN_OUTCOME -> blockUnknown -------------------

  @Test
  void completionUnknownOutcomeYieldsUnknownOutcome() {
    givenLockAcquired();
    givenAuthValid();
    givenPasswordValid();
    givenWriterApplies();
    when(completion.complete(any()))
        .thenReturn(Mono.just(ForgotPasswordCompletionResult.unknownOutcome()));
    StepVerifier.create(newOp().execute(input()))
        .assertNext(
            r ->
                assertThat(r.outcome())
                    .isEqualTo(
                        ForgotPasswordCompleteProtectedExecutionResult.Outcome.UNKNOWN_OUTCOME))
        .verifyComplete();
  }

  // ----- 12: completion onError -> blockUnknown --------------------------

  @Test
  void completionErrorYieldsUnknownOutcome() {
    givenLockAcquired();
    givenAuthValid();
    givenPasswordValid();
    givenWriterApplies();
    when(completion.complete(any()))
        .thenReturn(Mono.error(new IllegalStateException("boom")));
    StepVerifier.create(newOp().execute(input()))
        .assertNext(
            r ->
                assertThat(r.outcome())
                    .isEqualTo(
                        ForgotPasswordCompleteProtectedExecutionResult.Outcome.UNKNOWN_OUTCOME))
        .verifyComplete();
  }
}
