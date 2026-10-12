package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.verify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowContext;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowEncryptedContext;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordProtectedPhase;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.OtpVerificationOutcome;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.VerifyOtpChallengeResult;
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
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionExpiryRefreshPort;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionMutationLockPort;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort;
import com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordAuthFlowContextProtectorPort;
import com.bct.ngtpa.apiservice.application.port.out.OtpChallengePort;
import com.bct.ngtpa.apiservice.application.service.auth.forgotpassword.context.ForgotPasswordProtectedAuthFlowContextLoader;
import com.bct.ngtpa.apiservice.application.service.auth.forgotpassword.context.ForgotPasswordProtectedAuthFlowContextResult;
import com.bct.ngtpa.apiservice.application.service.auth.forgotpassword.context.ForgotPasswordProtectedAuthFlowContextStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Branch-oriented reactive tests for {@link ForgotPasswordOtpVerifyProtectedOperation}. Context
 * decryption is delegated to the mocked {@code loader} (no ciphertext/attribute wrangling needed,
 * unlike the resend/complete operations). {@code VerifyOtpChallengeResult.outcome()} constants are
 * exercised per the exhaustive switch confirmed in the production source.
 */
class ForgotPasswordOtpVerifyProtectedOperationTest {

  private static final Instant NOW = Instant.parse("2026-09-14T00:00:00Z");
  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
  private final Duration lease = Duration.ofSeconds(30);
  private final Duration resetAuthorizationTtl = Duration.ofMinutes(15);

  private final ForgotPasswordProtectedAuthFlowContextLoader loader =
      mock(ForgotPasswordProtectedAuthFlowContextLoader.class);
  private final ForgotPasswordAuthFlowContextProtectorPort protector =
      mock(ForgotPasswordAuthFlowContextProtectorPort.class);
  private final AuthFlowSessionPort sessions = mock(AuthFlowSessionPort.class);
  private final AuthFlowSessionMutationLockPort locks = mock(AuthFlowSessionMutationLockPort.class);
  private final AuthFlowSessionExpiryRefreshPort expiry =
      mock(AuthFlowSessionExpiryRefreshPort.class);
  private final OtpChallengePort otp = mock(OtpChallengePort.class);

  private final AuthFlowSessionId sid = new AuthFlowSessionId("afs-verify-1");

  private ForgotPasswordOtpVerifyProtectedOperation newOp() {
    return new ForgotPasswordOtpVerifyProtectedOperation(
        loader, protector, sessions, locks, expiry, otp, clock, lease, resetAuthorizationTtl);
  }

  // ----- fixtures -----------------------------------------------------------

  private AuthFlowSession sessionWith(
      AuthFlowScenario scenario,
      AuthFlowStatus status,
      Instant expiresAt,
      String activeChallengeId) {
    return new AuthFlowSession(
        sid,
        scenario,
        status,
        "csrfhash",
        activeChallengeId,
        new AuthFlowSessionData("email", "j***@example.com", Map.of()),
        NOW.minusSeconds(120),
        NOW.minusSeconds(60),
        expiresAt);
  }

  private AuthFlowSession validSession() {
    return sessionWith(
        AuthFlowScenario.FORGOT_PASSWORD,
        AuthFlowStatus.OTP_SENT,
        NOW.plusSeconds(1800),
        "challenge-1");
  }

  private AuthFlowSessionSnapshot snapshotOf(AuthFlowSession session) {
    return new AuthFlowSessionSnapshot(session, new SessionRevision("0".repeat(32)));
  }

  private ForgotPasswordAuthFlowContext validContext() {
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
        null,
        null,
        "v1",
        ForgotPasswordProtectedPhase.OTP_SENT);
  }

  private VerifyForgotPasswordOtpCommand command(String code) {
    return new VerifyForgotPasswordOtpCommand(
        sid,
        "csrf-token",
        code,
        "https://portal.bcthk.com",
        "https://portal.bcthk.com/reset",
        "idem-1",
        "en",
        "req-1",
        Duration.ofMinutes(5));
  }

  private VerifyForgotPasswordOtpCommand validCommand() {
    return command("123456");
  }

  // ----- shared stubbing helpers --------------------------------------------

  private void givenLockAcquired() {
    when(locks.acquire(any(), any()))
        .thenReturn(Mono.just(new SessionLockHandle(true, "lock-key", "lock-token")));
    when(locks.release(any())).thenReturn(Mono.just(true));
  }

  private void givenInitialSession(AuthFlowSession session) {
    when(sessions.loadSnapshot(any())).thenReturn(Mono.just(Optional.of(snapshotOf(session))));
  }

  private void givenLoaderValid() {
    when(loader.load(any()))
        .thenReturn(
            Mono.just(
                ForgotPasswordProtectedAuthFlowContextResult.valid("challenge-1", validContext())));
  }

  private void givenWriterAndExpirySucceed() {
    when(sessions.updateRevisioned(any()))
        .thenAnswer(
            invocation ->
                Mono.just(
                    new AuthFlowSessionMutationResult(
                        AuthFlowSessionMutationOutcome.APPLIED, snapshotOf(validSession()))));
    when(protector.protect(any()))
        .thenReturn(new ForgotPasswordAuthFlowEncryptedContext("ct", "kv", "AES_GCM_V1"));
    when(expiry.refresh(any(), any())).thenReturn(Mono.just(validSession()));
  }

  private VerifyOtpChallengeResult challengeResult(OtpVerificationOutcome outcome, int remaining) {
    return new VerifyOtpChallengeResult("challenge-1", outcome, remaining, null);
  }

  // ----- tests: guards -------------------------------------------------------

  @Test
  void nullCommandYieldsAuthFlowInvalid() {
    StepVerifier.create(newOp().execute(null))
        .assertNext(
            r -> {
              assertThat(r.result().status())
                  .isEqualTo(VerifyForgotPasswordOtpStatus.AUTH_FLOW_INVALID);
              assertThat(r.result().nextStep()).isEqualTo(WorkflowNextStep.NONE);
            })
        .verifyComplete();
  }

  @Test
  void invalidOtpFormatYieldsOtpInvalid() {
    StepVerifier.create(newOp().execute(command("12a456")))
        .assertNext(
            r -> {
              assertThat(r.result().status()).isEqualTo(VerifyForgotPasswordOtpStatus.OTP_INVALID);
              assertThat(r.result().nextStep()).isEqualTo(WorkflowNextStep.VERIFY_OTP);
            })
        .verifyComplete();
  }

  @Test
  void shortOtpFormatYieldsOtpInvalid() {
    StepVerifier.create(newOp().execute(command("12345")))
        .assertNext(
            r ->
                assertThat(r.result().status())
                    .isEqualTo(VerifyForgotPasswordOtpStatus.OTP_INVALID))
        .verifyComplete();
  }

  @Test
  void lockNotAcquiredYieldsAuthFlowInvalid() {
    when(locks.acquire(any(), any())).thenReturn(Mono.just(SessionLockHandle.notAcquired()));
    StepVerifier.create(newOp().execute(validCommand()))
        .assertNext(
            r ->
                assertThat(r.result().status())
                    .isEqualTo(VerifyForgotPasswordOtpStatus.AUTH_FLOW_INVALID))
        .verifyComplete();
  }

  // ----- tests: initial session gate -----------------------------------------

  @Test
  void initialSessionMissingYieldsAuthFlowInvalid() {
    givenLockAcquired();
    when(sessions.loadSnapshot(any())).thenReturn(Mono.just(Optional.empty()));
    StepVerifier.create(newOp().execute(validCommand()))
        .assertNext(
            r ->
                assertThat(r.result().status())
                    .isEqualTo(VerifyForgotPasswordOtpStatus.AUTH_FLOW_INVALID))
        .verifyComplete();
  }

  @Test
  void initialSessionExpiredYieldsAuthFlowInvalid() {
    givenLockAcquired();
    givenInitialSession(
        sessionWith(
            AuthFlowScenario.FORGOT_PASSWORD,
            AuthFlowStatus.OTP_SENT,
            NOW.minusSeconds(1),
            "challenge-1"));
    StepVerifier.create(newOp().execute(validCommand()))
        .assertNext(
            r ->
                assertThat(r.result().status())
                    .isEqualTo(VerifyForgotPasswordOtpStatus.AUTH_FLOW_INVALID))
        .verifyComplete();
  }

  @Test
  void initialSessionWrongScenarioYieldsAuthFlowInvalid() {
    givenLockAcquired();
    givenInitialSession(
        sessionWith(
            AuthFlowScenario.LOGIN, AuthFlowStatus.OTP_SENT, NOW.plusSeconds(1800), "challenge-1"));
    StepVerifier.create(newOp().execute(validCommand()))
        .assertNext(
            r ->
                assertThat(r.result().status())
                    .isEqualTo(VerifyForgotPasswordOtpStatus.AUTH_FLOW_INVALID))
        .verifyComplete();
  }

  @Test
  void initialSessionWrongStatusYieldsAuthFlowInvalid() {
    givenLockAcquired();
    givenInitialSession(
        sessionWith(
            AuthFlowScenario.FORGOT_PASSWORD,
            AuthFlowStatus.RESET_AUTHORIZED,
            NOW.plusSeconds(1800),
            "challenge-1"));
    StepVerifier.create(newOp().execute(validCommand()))
        .assertNext(
            r ->
                assertThat(r.result().status())
                    .isEqualTo(VerifyForgotPasswordOtpStatus.AUTH_FLOW_INVALID))
        .verifyComplete();
  }

  // ----- tests: loader mapping ------------------------------------------------

  @Test
  void loaderCsrfInvalidYieldsCsrfInvalid() {
    givenLockAcquired();
    givenInitialSession(validSession());
    when(loader.load(any()))
        .thenReturn(
            Mono.just(
                ForgotPasswordProtectedAuthFlowContextResult.invalid(
                    ForgotPasswordProtectedAuthFlowContextStatus.CSRF_INVALID)));
    StepVerifier.create(newOp().execute(validCommand()))
        .assertNext(
            r ->
                assertThat(r.result().status())
                    .isEqualTo(VerifyForgotPasswordOtpStatus.CSRF_INVALID))
        .verifyComplete();
  }

  @Test
  void loaderOriginRefererInvalidYieldsCsrfInvalid() {
    givenLockAcquired();
    givenInitialSession(validSession());
    when(loader.load(any()))
        .thenReturn(
            Mono.just(
                ForgotPasswordProtectedAuthFlowContextResult.invalid(
                    ForgotPasswordProtectedAuthFlowContextStatus.ORIGIN_REFERER_INVALID)));
    StepVerifier.create(newOp().execute(validCommand()))
        .assertNext(
            r ->
                assertThat(r.result().status())
                    .isEqualTo(VerifyForgotPasswordOtpStatus.CSRF_INVALID))
        .verifyComplete();
  }

  @Test
  void loaderOtherFailureYieldsAuthFlowInvalid() {
    givenLockAcquired();
    givenInitialSession(validSession());
    when(loader.load(any()))
        .thenReturn(
            Mono.just(
                ForgotPasswordProtectedAuthFlowContextResult.invalid(
                    ForgotPasswordProtectedAuthFlowContextStatus.MISSING_CONTEXT)));
    StepVerifier.create(newOp().execute(validCommand()))
        .assertNext(
            r ->
                assertThat(r.result().status())
                    .isEqualTo(VerifyForgotPasswordOtpStatus.AUTH_FLOW_INVALID))
        .verifyComplete();
  }

  // ----- tests: context/phase guards -----------------------------------------

  @Test
  void wrongPhaseYieldsAuthFlowInvalid() {
    givenLockAcquired();
    givenInitialSession(validSession());
    ForgotPasswordAuthFlowContext wrongPhase =
        new ForgotPasswordAuthFlowContext(
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
            null,
            null,
            "v1",
            ForgotPasswordProtectedPhase.IDENTITY_VERIFIED);
    when(loader.load(any()))
        .thenReturn(
            Mono.just(
                ForgotPasswordProtectedAuthFlowContextResult.valid("challenge-1", wrongPhase)));
    StepVerifier.create(newOp().execute(validCommand()))
        .assertNext(
            r ->
                assertThat(r.result().status())
                    .isEqualTo(VerifyForgotPasswordOtpStatus.AUTH_FLOW_INVALID))
        .verifyComplete();
  }

  // NOTE: there is intentionally no sibling test for the operation's
  // `blank(resetSubjectId)` guard sub-condition. It is UNREACHABLE via any
  // validly-constructed ForgotPasswordAuthFlowContext: identityValue is a
  // required, always-non-blank field (enforced by the record's compact
  // constructor), and effectiveResetSubjectId(...) falls back to
  // identityValue whenever resetSubjectId is blank - so the effective value
  // can never be blank. This is dead defensive code (same category as the
  // unreachable `result == null` branch noted for the COMPLETE operation).
  // See docs/coverage/code-coverage-95-plan.md section 9c.
  @Test
  void blankActiveChallengeIdFromLoaderYieldsAuthFlowInvalid() {
    givenLockAcquired();
    givenInitialSession(validSession());
    when(loader.load(any()))
        .thenReturn(
            Mono.just(ForgotPasswordProtectedAuthFlowContextResult.valid(null, validContext())));
    StepVerifier.create(newOp().execute(validCommand()))
        .assertNext(
            r ->
                assertThat(r.result().status())
                    .isEqualTo(VerifyForgotPasswordOtpStatus.AUTH_FLOW_INVALID))
        .verifyComplete();
  }

  // ----- tests: re-loaded session gate ----------------------------------------

  @Test
  void reloadedSessionMissingYieldsAuthFlowInvalid() {
    givenLockAcquired();
    givenLoaderValid();
    when(sessions.loadSnapshot(any()))
        .thenReturn(Mono.just(Optional.of(snapshotOf(validSession()))))
        .thenReturn(Mono.just(Optional.empty()));
    StepVerifier.create(newOp().execute(validCommand()))
        .assertNext(
            r ->
                assertThat(r.result().status())
                    .isEqualTo(VerifyForgotPasswordOtpStatus.AUTH_FLOW_INVALID))
        .verifyComplete();
  }

  // ----- tests: OTP verification outcomes -------------------------------------

  @Test
  void verifyOutcomeExpiredYieldsOtpExpired() {
    givenLockAcquired();
    givenInitialSession(validSession());
    givenLoaderValid();
    when(otp.verifyChallenge(any()))
        .thenReturn(Mono.just(challengeResult(OtpVerificationOutcome.EXPIRED, 2)));
    StepVerifier.create(newOp().execute(validCommand()))
        .assertNext(
            r -> {
              assertThat(r.result().status()).isEqualTo(VerifyForgotPasswordOtpStatus.OTP_EXPIRED);
              assertThat(r.result().nextStep()).isEqualTo(WorkflowNextStep.RESEND_OTP);
              assertThat(r.result().remainingVerifyAttempts()).isEqualTo(2);
            })
        .verifyComplete();
  }

  @Test
  void verifyOutcomeLockedYieldsOtpLocked() {
    givenLockAcquired();
    givenInitialSession(validSession());
    givenLoaderValid();
    when(otp.verifyChallenge(any()))
        .thenReturn(Mono.just(challengeResult(OtpVerificationOutcome.LOCKED, 0)));
    StepVerifier.create(newOp().execute(validCommand()))
        .assertNext(
            r -> {
              assertThat(r.result().status()).isEqualTo(VerifyForgotPasswordOtpStatus.OTP_LOCKED);
              assertThat(r.result().nextStep()).isEqualTo(WorkflowNextStep.RESTART_FLOW);
            })
        .verifyComplete();
  }

  @Test
  void verifyOutcomeInvalidYieldsOtpInvalid() {
    givenLockAcquired();
    givenInitialSession(validSession());
    givenLoaderValid();
    when(otp.verifyChallenge(any()))
        .thenReturn(Mono.just(challengeResult(OtpVerificationOutcome.INVALID, 3)));
    StepVerifier.create(newOp().execute(validCommand()))
        .assertNext(
            r -> {
              assertThat(r.result().status()).isEqualTo(VerifyForgotPasswordOtpStatus.OTP_INVALID);
              assertThat(r.result().nextStep()).isEqualTo(WorkflowNextStep.VERIFY_OTP);
            })
        .verifyComplete();
  }

  @Test
  void verifyOutcomeNotFoundYieldsOtpInvalid() {
    givenLockAcquired();
    givenInitialSession(validSession());
    givenLoaderValid();
    when(otp.verifyChallenge(any()))
        .thenReturn(Mono.just(challengeResult(OtpVerificationOutcome.NOT_FOUND, 0)));
    StepVerifier.create(newOp().execute(validCommand()))
        .assertNext(
            r ->
                assertThat(r.result().status())
                    .isEqualTo(VerifyForgotPasswordOtpStatus.OTP_INVALID))
        .verifyComplete();
  }

  // ----- tests: happy path + writer/expiry failure ----------------------------

  @Test
  void verifyOutcomeValidYieldsVerified() {
    givenLockAcquired();
    givenInitialSession(validSession());
    givenLoaderValid();
    givenWriterAndExpirySucceed();
    when(otp.verifyChallenge(any()))
        .thenReturn(Mono.just(challengeResult(OtpVerificationOutcome.VALID, 0)));
    StepVerifier.create(newOp().execute(validCommand()))
        .assertNext(
            r -> {
              assertThat(r.result().status()).isEqualTo(VerifyForgotPasswordOtpStatus.VERIFIED);
              assertThat(r.result().nextStep()).isEqualTo(WorkflowNextStep.PASSWORD_SETUP);
            })
        .verifyComplete();
  }

  @Test
  void authorizeExpiryRefreshFailureYieldsSystemFailed() {
    givenLockAcquired();
    givenInitialSession(validSession());
    givenLoaderValid();
    givenWriterAndExpirySucceed();
    when(expiry.refresh(any(), any())).thenReturn(Mono.error(new IllegalStateException("boom")));
    when(otp.verifyChallenge(any()))
        .thenReturn(Mono.just(challengeResult(OtpVerificationOutcome.VALID, 0)));
    StepVerifier.create(newOp().execute(validCommand()))
        .assertNext(
            r ->
                assertThat(r.result().status())
                    .isEqualTo(VerifyForgotPasswordOtpStatus.SYSTEM_FAILED))
        .verifyComplete();
  }

  @Test
  void authorizeWriterFailureYieldsSystemFailed() {
    givenLockAcquired();
    givenInitialSession(validSession());
    givenLoaderValid();
    givenWriterAndExpirySucceed();
    // Stub the writer's underlying updateRevisioned(...) to error directly rather than
    // guessing a second AuthFlowSessionMutationOutcome enum constant beyond the confirmed
    // APPLIED (avoids introducing an unverified assumption into a security-critical test).
    when(sessions.updateRevisioned(any()))
        .thenReturn(Mono.error(new IllegalStateException("mutation failed")));
    when(otp.verifyChallenge(any()))
        .thenReturn(Mono.just(challengeResult(OtpVerificationOutcome.VALID, 0)));
    StepVerifier.create(newOp().execute(validCommand()))
        .assertNext(
            r ->
                assertThat(r.result().status())
                    .isEqualTo(VerifyForgotPasswordOtpStatus.SYSTEM_FAILED))
        .verifyComplete();
  }

  // ----- tests: outer error handling -------------------------------------------

  @Test
  void lockAcquireErrorYieldsSystemFailed() {
    when(locks.acquire(any(), any())).thenReturn(Mono.error(new IllegalStateException("boom")));
    StepVerifier.create(newOp().execute(validCommand()))
        .assertNext(
            r ->
                assertThat(r.result().status())
                    .isEqualTo(VerifyForgotPasswordOtpStatus.SYSTEM_FAILED))
        .verifyComplete();
  }

  @Test
  void loadSnapshotErrorYieldsSystemFailed() {
    givenLockAcquired();
    when(sessions.loadSnapshot(any())).thenReturn(Mono.error(new IllegalStateException("boom")));
    StepVerifier.create(newOp().execute(validCommand()))
        .assertNext(
            r ->
                assertThat(r.result().status())
                    .isEqualTo(VerifyForgotPasswordOtpStatus.SYSTEM_FAILED))
        .verifyComplete();
  }

  // ----- tests: trust and scheme fields survive the reset-subject normalization (scripts 188-190) ----

  @Test
  void verifyKeepsTheTrustAndSchemeFieldsWhenTheResetSubjectIdIsNormalized() {
    givenLockAcquired();
    givenInitialSession(validSession());
    // START stores no resetSubjectId; the operation falls back to the identity value and rebuilds the
    // context, which must carry the trust and scheme fields across the rebuild.
    ForgotPasswordAuthFlowContext withoutResetSubject =
        new ForgotPasswordAuthFlowContext(
            "john",
            "idv-1",
            "CUSTID",
            true,
            null,
            "email",
            "john@example.com",
            "j***@example.com",
            null,
            "HSBC",
            "Scheme En",
            "Scheme Chi",
            "v1",
            ForgotPasswordProtectedPhase.OTP_SENT);
    when(loader.load(any()))
        .thenReturn(
            Mono.just(
                ForgotPasswordProtectedAuthFlowContextResult.valid(
                    "challenge-1", withoutResetSubject)));
    givenWriterAndExpirySucceed();
    when(otp.verifyChallenge(any()))
        .thenReturn(Mono.just(challengeResult(OtpVerificationOutcome.VALID, 0)));

    assertThat(newOp().execute(validCommand()).block().result().status())
        .as("verify outcome")
        .isEqualTo(VerifyForgotPasswordOtpStatus.VERIFIED);

    ArgumentCaptor<ForgotPasswordAuthFlowContext> persisted =
        ArgumentCaptor.forClass(ForgotPasswordAuthFlowContext.class);
    verify(protector).protect(persisted.capture());
    ForgotPasswordAuthFlowContext saved = persisted.getValue();
    assertThat(saved.phase())
        .as("persisted phase")
        .isEqualTo(ForgotPasswordProtectedPhase.PASSWORD_SETUP);
    assertThat(saved.resetSubjectId()).as("persisted resetSubjectId").isEqualTo("idv-1");
    assertThat(saved.trustCode()).as("persisted trustCode").isEqualTo("HSBC");
    assertThat(saved.schemeName()).as("persisted schemeName").isEqualTo("Scheme En");
    assertThat(saved.schemeNameChi()).as("persisted schemeNameChi").isEqualTo("Scheme Chi");
  }
}
