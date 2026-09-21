package com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryCancelCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryCancelResult;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowScenario;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionData;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionMutationOutcome;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionMutationResult;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionSnapshot;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowStatus;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.SessionRevision;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort;
import com.bct.ngtpa.apiservice.application.service.auth.usernamerecovery.context.UsernameRecoveryAuthFlowContextBinding;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.CsrfTokenValidationService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Branch-oriented reactive tests for {@link UsernameRecoveryCancelProtectedOperation}. All types
 * verified verbatim (rounds 96-97). {@code originRefererValidator} is instantiated internally by
 * the operation (new OriginRefererValidator()) - tests use REAL allowed-origin lists rather than
 * mocking it, matching the confirmed real matching rule (rounds 12-14 of the earlier
 * OTP-resend investigation).
 */
class UsernameRecoveryCancelProtectedOperationTest {

  private static final Instant NOW = Instant.parse("2026-09-14T00:00:00Z");
  private static final String ALLOWED_ORIGIN = "https://portal.bcthk.com";

  private final AuthFlowSessionPort sessions = mock(AuthFlowSessionPort.class);
  private final UsernameRecoveryAuthFlowContextBinding contextBinding =
      mock(UsernameRecoveryAuthFlowContextBinding.class);
  private final CsrfTokenValidationService csrfTokenValidationService =
      mock(CsrfTokenValidationService.class);

  private final AuthFlowSessionId sid = new AuthFlowSessionId("afs-unamerec-cancel-1");

  private UsernameRecoveryCancelProtectedOperation newOperation() {
    return newOperation(List.of(ALLOWED_ORIGIN));
  }

  private UsernameRecoveryCancelProtectedOperation newOperation(List<String> allowedOrigins) {
    return new UsernameRecoveryCancelProtectedOperation(
        sessions, contextBinding, csrfTokenValidationService, allowedOrigins);
  }

  private UsernameRecoveryCancelCommand command() {
    return new UsernameRecoveryCancelCommand(sid, "idem-1");
  }

  private AuthFlowSession sessionWith(
      AuthFlowScenario scenario, AuthFlowStatus status, Instant expiresAt) {
    return new AuthFlowSession(
        sid, scenario, status, "csrfhash", null,
        new AuthFlowSessionData("email", "j***@example.com", Map.of()),
        NOW.minusSeconds(300), NOW.minusSeconds(60), expiresAt);
  }

  private AuthFlowSessionSnapshot snapshotOf(AuthFlowSession session) {
    return new AuthFlowSessionSnapshot(session, new SessionRevision("0".repeat(32)));
  }

  private void givenSession(AuthFlowSession session) {
    when(sessions.loadSnapshot(any())).thenReturn(Mono.just(Optional.of(snapshotOf(session))));
  }

  // ----- guards ---------------------------------------------------------------

  @Test
  void nullCommandYieldsValidationFailed() {
    StepVerifier.create(newOperation().execute(null, "csrf", ALLOWED_ORIGIN, "ref", NOW))
        .expectError(UsernameRecoveryCancelException.class)
        .verify();
  }

  @Test
  void nullNowYieldsValidationFailed() {
    StepVerifier.create(newOperation().execute(command(), "csrf", ALLOWED_ORIGIN, "ref", null))
        .expectError(UsernameRecoveryCancelException.class)
        .verify();
  }

  // ----- absent session: idempotent success ------------------------------------

  @Test
  void sessionMissingYieldsSuccess() {
    when(sessions.loadSnapshot(any())).thenReturn(Mono.just(Optional.empty()));
    StepVerifier.create(newOperation().execute(command(), "csrf", ALLOWED_ORIGIN, "ref", NOW))
        .assertNext(
            r -> {
              assertThat(r.completed()).isTrue();
              assertThat(r.result().outcome()).isEqualTo(UsernameRecoveryCancelResult.Outcome.SUCCESS);
            })
        .verifyComplete();
  }

  // ----- session identity / scenario mismatch -> system failure -------------------

  @Test
  void sessionIdMismatchYieldsSystemFailure() {
    AuthFlowSession mismatched =
        new AuthFlowSession(
            new AuthFlowSessionId("afs-different"), AuthFlowScenario.USERNAME_RECOVERY,
            AuthFlowStatus.STARTED, "csrfhash", null,
            new AuthFlowSessionData("email", "j***@example.com", Map.of()),
            NOW.minusSeconds(300), NOW.minusSeconds(60), NOW.plusSeconds(1800));
    givenSession(mismatched);
    StepVerifier.create(newOperation().execute(command(), "csrf", ALLOWED_ORIGIN, "ref", NOW))
        .assertNext(
            r -> assertThat(r.failureBoundary())
                .isEqualTo(
                    UsernameRecoveryCancelProtectedExecutionResult.FailureBoundary
                        .PROVEN_PRE_DISPATCH))
        .verifyComplete();
  }

  @Test
  void wrongScenarioYieldsSystemFailure() {
    givenSession(sessionWith(AuthFlowScenario.LOGIN, AuthFlowStatus.STARTED, NOW.plusSeconds(1800)));
    StepVerifier.create(newOperation().execute(command(), "csrf", ALLOWED_ORIGIN, "ref", NOW))
        .assertNext(
            r -> assertThat(r.failureBoundary())
                .isEqualTo(
                    UsernameRecoveryCancelProtectedExecutionResult.FailureBoundary
                        .PROVEN_PRE_DISPATCH))
        .verifyComplete();
  }

  // ----- expired / terminal (non-CANCELLED) session -> idempotent success -----------

  @Test
  void expiredSessionYieldsSuccess() {
    givenSession(
        sessionWith(AuthFlowScenario.USERNAME_RECOVERY, AuthFlowStatus.STARTED, NOW.minusSeconds(1)));
    StepVerifier.create(newOperation().execute(command(), "csrf", ALLOWED_ORIGIN, "ref", NOW))
        .assertNext(r -> assertThat(r.result().outcome()).isEqualTo(UsernameRecoveryCancelResult.Outcome.SUCCESS))
        .verifyComplete();
  }

  @Test
  void terminalCompletedSessionYieldsSuccess() {
    givenSession(
        sessionWith(AuthFlowScenario.USERNAME_RECOVERY, AuthFlowStatus.COMPLETED, NOW.plusSeconds(1800)));
    StepVerifier.create(newOperation().execute(command(), "csrf", ALLOWED_ORIGIN, "ref", NOW))
        .assertNext(r -> assertThat(r.result().outcome()).isEqualTo(UsernameRecoveryCancelResult.Outcome.SUCCESS))
        .verifyComplete();
  }

  // ----- already-cancelled: idempotent success via validateContext -----------------

  @Test
  void alreadyCancelledYieldsSuccessAndLoadsContext() {
    AuthFlowSession cancelled =
        sessionWith(AuthFlowScenario.USERNAME_RECOVERY, AuthFlowStatus.CANCELLED, NOW.plusSeconds(1800));
    givenSession(cancelled);
    StepVerifier.create(newOperation().execute(command(), "csrf", ALLOWED_ORIGIN, "ref", NOW))
        .assertNext(r -> assertThat(r.result().outcome()).isEqualTo(UsernameRecoveryCancelResult.Outcome.SUCCESS))
        .verifyComplete();
    verify(contextBinding, times(1)).load(any());
  }

  @Test
  void alreadyCancelledContextLoadFailsYieldsProvenPreDispatch() {
    AuthFlowSession cancelled =
        sessionWith(AuthFlowScenario.USERNAME_RECOVERY, AuthFlowStatus.CANCELLED, NOW.plusSeconds(1800));
    givenSession(cancelled);
    doThrow(new IllegalStateException("boom")).when(contextBinding).load(any());
    // NOTE (round 99): execute()'s outer onErrorResume catches ANY
    // non-UsernameRecoveryCancelException and completes with a PROVEN_PRE_DISPATCH
    // result - it never propagates as a reactive error signal.
    StepVerifier.create(newOperation().execute(command(), "csrf", ALLOWED_ORIGIN, "ref", NOW))
        .assertNext(
            r -> assertThat(r.failureBoundary())
                .isEqualTo(
                    UsernameRecoveryCancelProtectedExecutionResult.FailureBoundary
                        .PROVEN_PRE_DISPATCH))
        .verifyComplete();
  }

  // ----- security gates (origin, csrf) ------------------------------------------

  @Test
  void disallowedOriginYieldsSecurityInvalid() {
    givenSession(sessionWith(AuthFlowScenario.USERNAME_RECOVERY, AuthFlowStatus.STARTED, NOW.plusSeconds(1800)));
    StepVerifier.create(
            newOperation().execute(command(), "csrf", "https://evil.example.com", "ref", NOW))
        .assertNext(
            r -> assertThat(r.failureBoundary())
                .isEqualTo(
                    UsernameRecoveryCancelProtectedExecutionResult.FailureBoundary
                        .PROVEN_PRE_DISPATCH))
        .verifyComplete();
  }

  @Test
  void noAllowedOriginsSkipsOriginCheck() {
    givenSession(sessionWith(AuthFlowScenario.USERNAME_RECOVERY, AuthFlowStatus.STARTED, NOW.plusSeconds(1800)));
    when(csrfTokenValidationService.validate(any(), any()))
        .thenReturn(mock(com.bct.ngtpa.apiservice.application.usecase.session.authflow.CsrfTokenValidationResult.class));
    when(csrfTokenValidationService.validate(any(), any()).valid()).thenReturn(true);
    when(sessions.cancelRevisioned(any(), any()))
        .thenReturn(
            Mono.just(
                new AuthFlowSessionMutationResult(
                    AuthFlowSessionMutationOutcome.APPLIED,
                    snapshotOf(sessionWith(AuthFlowScenario.USERNAME_RECOVERY, AuthFlowStatus.CANCELLED, NOW.plusSeconds(1800))))));
    StepVerifier.create(newOperation(List.of()).execute(command(), "csrf", "https://evil.example.com", "ref", NOW))
        .assertNext(r -> assertThat(r.result().outcome()).isEqualTo(UsernameRecoveryCancelResult.Outcome.SUCCESS))
        .verifyComplete();
  }

  @Test
  void invalidCsrfYieldsSecurityInvalid() {
    givenSession(sessionWith(AuthFlowScenario.USERNAME_RECOVERY, AuthFlowStatus.STARTED, NOW.plusSeconds(1800)));
    var invalidResult =
        mock(com.bct.ngtpa.apiservice.application.usecase.session.authflow.CsrfTokenValidationResult.class);
    when(invalidResult.valid()).thenReturn(false);
    when(csrfTokenValidationService.validate(any(), any())).thenReturn(invalidResult);
    StepVerifier.create(newOperation().execute(command(), "wrong-csrf", ALLOWED_ORIGIN, "ref", NOW))
        .assertNext(
            r -> assertThat(r.failureBoundary())
                .isEqualTo(
                    UsernameRecoveryCancelProtectedExecutionResult.FailureBoundary
                        .PROVEN_PRE_DISPATCH))
        .verifyComplete();
    verify(sessions, never()).cancelRevisioned(any(), any());
  }

  // ----- happy path + mutation outcomes ------------------------------------------

  private void givenCsrfValid() {
    var validResult =
        mock(com.bct.ngtpa.apiservice.application.usecase.session.authflow.CsrfTokenValidationResult.class);
    when(validResult.valid()).thenReturn(true);
    when(csrfTokenValidationService.validate(any(), any())).thenReturn(validResult);
  }

  @Test
  void contextLoadFailureBeforeMutationYieldsProvenPreDispatch() {
    givenSession(sessionWith(AuthFlowScenario.USERNAME_RECOVERY, AuthFlowStatus.STARTED, NOW.plusSeconds(1800)));
    givenCsrfValid();
    doThrow(new IllegalStateException("boom")).when(contextBinding).load(any());
    // NOTE (round 99): this load() call is the EAGER pre-mutation check inside
    // handle()'s own try/catch, which explicitly produces PROVEN_PRE_DISPATCH
    // (cancelRevisioned() is never reached).
    StepVerifier.create(newOperation().execute(command(), "csrf", ALLOWED_ORIGIN, "ref", NOW))
        .assertNext(
            r -> assertThat(r.failureBoundary())
                .isEqualTo(
                    UsernameRecoveryCancelProtectedExecutionResult.FailureBoundary
                        .PROVEN_PRE_DISPATCH))
        .verifyComplete();
  }

  @Test
  void mutationNotAppliedYieldsPossibleSideEffect() {
    givenSession(sessionWith(AuthFlowScenario.USERNAME_RECOVERY, AuthFlowStatus.STARTED, NOW.plusSeconds(1800)));
    givenCsrfValid();
    when(sessions.cancelRevisioned(any(), any()))
        .thenReturn(
            Mono.just(
                new AuthFlowSessionMutationResult(
                    AuthFlowSessionMutationOutcome.SESSION_REVISION_CHANGED,
                    snapshotOf(sessionWith(AuthFlowScenario.USERNAME_RECOVERY, AuthFlowStatus.STARTED, NOW.plusSeconds(1800))))));
    StepVerifier.create(newOperation().execute(command(), "csrf", ALLOWED_ORIGIN, "ref", NOW))
        .assertNext(
            r -> assertThat(r.failureBoundary())
                .isEqualTo(
                    UsernameRecoveryCancelProtectedExecutionResult.FailureBoundary
                        .POSSIBLE_SIDE_EFFECT))
        .verifyComplete();
  }

  @Test
  void mutationErrorYieldsPossibleSideEffect() {
    givenSession(sessionWith(AuthFlowScenario.USERNAME_RECOVERY, AuthFlowStatus.STARTED, NOW.plusSeconds(1800)));
    givenCsrfValid();
    when(sessions.cancelRevisioned(any(), any())).thenReturn(Mono.error(new IllegalStateException("boom")));
    StepVerifier.create(newOperation().execute(command(), "csrf", ALLOWED_ORIGIN, "ref", NOW))
        .assertNext(
            r -> assertThat(r.failureBoundary())
                .isEqualTo(
                    UsernameRecoveryCancelProtectedExecutionResult.FailureBoundary
                        .POSSIBLE_SIDE_EFFECT))
        .verifyComplete();
  }

  @Test
  void appliedMutationWithDiscontinuousEnvelopeYieldsPossibleSideEffect() {
    AuthFlowSession original =
        sessionWith(AuthFlowScenario.USERNAME_RECOVERY, AuthFlowStatus.STARTED, NOW.plusSeconds(1800));
    givenSession(original);
    givenCsrfValid();
    AuthFlowSession discontinuous =
        new AuthFlowSession(
            sid, AuthFlowScenario.USERNAME_RECOVERY, AuthFlowStatus.CANCELLED, "csrfhash", null,
            new AuthFlowSessionData("email", "j***@example.com", Map.of("x", "y")),
            NOW.minusSeconds(300), NOW.minusSeconds(60), NOW.plusSeconds(9999));
    when(sessions.cancelRevisioned(any(), any()))
        .thenReturn(
            Mono.just(
                new AuthFlowSessionMutationResult(
                    AuthFlowSessionMutationOutcome.APPLIED, snapshotOf(discontinuous))));
    StepVerifier.create(newOperation().execute(command(), "csrf", ALLOWED_ORIGIN, "ref", NOW))
        .assertNext(
            r -> assertThat(r.failureBoundary())
                .isEqualTo(
                    UsernameRecoveryCancelProtectedExecutionResult.FailureBoundary
                        .POSSIBLE_SIDE_EFFECT))
        .verifyComplete();
  }

  @Test
  void appliedMutationSucceedsAndLoadsContext() {
    AuthFlowSession original =
        sessionWith(AuthFlowScenario.USERNAME_RECOVERY, AuthFlowStatus.STARTED, NOW.plusSeconds(1800));
    givenSession(original);
    givenCsrfValid();
    AuthFlowSession cancelled =
        new AuthFlowSession(
            sid, AuthFlowScenario.USERNAME_RECOVERY, AuthFlowStatus.CANCELLED, "csrfhash", null,
            original.data(), original.createdAt(), NOW, original.expiresAt());
    when(sessions.cancelRevisioned(any(), any()))
        .thenReturn(
            Mono.just(
                new AuthFlowSessionMutationResult(
                    AuthFlowSessionMutationOutcome.APPLIED, snapshotOf(cancelled))));
    StepVerifier.create(newOperation().execute(command(), "csrf", ALLOWED_ORIGIN, "ref", NOW))
        .assertNext(r -> assertThat(r.result().outcome()).isEqualTo(UsernameRecoveryCancelResult.Outcome.SUCCESS))
        .verifyComplete();
    // NOTE (round 99): load() is called TWICE in the real happy path - once
    // eagerly inline in handle() right after CSRF validation succeeds (a
    // pre-mutation validation), and again in validateCancelled() after the
    // mutation applies.
    verify(contextBinding, times(2)).load(any());
  }

  @Test
  void appliedMutationContextLoadFailsYieldsPossibleSideEffect() {
    AuthFlowSession original =
        sessionWith(AuthFlowScenario.USERNAME_RECOVERY, AuthFlowStatus.STARTED, NOW.plusSeconds(1800));
    givenSession(original);
    givenCsrfValid();
    AuthFlowSession cancelled =
        new AuthFlowSession(
            sid, AuthFlowScenario.USERNAME_RECOVERY, AuthFlowStatus.CANCELLED, "csrfhash", null,
            original.data(), original.createdAt(), NOW, original.expiresAt());
    when(sessions.cancelRevisioned(any(), any()))
        .thenReturn(
            Mono.just(
                new AuthFlowSessionMutationResult(
                    AuthFlowSessionMutationOutcome.APPLIED, snapshotOf(cancelled))));
    // NOTE (round 99): succeed on the 1st (pre-mutation, eager) load() call so
    // cancelRevisioned() is actually reached, and only throw on the 2nd
    // (post-mutation, validateCancelled) call - isolating the POSSIBLE_SIDE_EFFECT
    // branch specifically. An unconditional throw would short-circuit on the
    // FIRST call, before cancelRevisioned() is ever invoked.
    // NOTE (round 100): doNothing() only works on VOID methods -
    // UsernameRecoveryAuthFlowContextBinding.load(...) returns
    // UsernameRecoveryAuthFlowContext (confirmed non-void, round 97), so the
    // 1st call must be stubbed with doReturn(...) instead, returning a real
    // (mocked) context object; the 2nd call still throws as intended.
    org.mockito.Mockito.doReturn(
            mock(
                com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.context
                    .UsernameRecoveryAuthFlowContext.class))
        .doThrow(new IllegalStateException("boom"))
        .when(contextBinding)
        .load(any());
    StepVerifier.create(newOperation().execute(command(), "csrf", ALLOWED_ORIGIN, "ref", NOW))
        .assertNext(
            r -> assertThat(r.failureBoundary())
                .isEqualTo(
                    UsernameRecoveryCancelProtectedExecutionResult.FailureBoundary
                        .POSSIBLE_SIDE_EFFECT))
        .verifyComplete();
  }

  @Test
  void loadSnapshotErrorYieldsProvenPreDispatch() {
    when(sessions.loadSnapshot(any())).thenReturn(Mono.error(new IllegalStateException("boom")));
    // NOTE (round 99): a loadSnapshot() error happens BEFORE handle() is ever
    // called, so it is caught by the same outer catch-all as any other
    // pre-dispatch failure, yielding PROVEN_PRE_DISPATCH (not POSSIBLE_SIDE_EFFECT).
    StepVerifier.create(newOperation().execute(command(), "csrf", ALLOWED_ORIGIN, "ref", NOW))
        .assertNext(
            r -> assertThat(r.failureBoundary())
                .isEqualTo(
                    UsernameRecoveryCancelProtectedExecutionResult.FailureBoundary
                        .PROVEN_PRE_DISPATCH))
        .verifyComplete();
  }
}
