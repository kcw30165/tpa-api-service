package com.bct.ngtpa.apiservice.adapter.out.session.forgotpassword;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowContext;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowEncryptedContext;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordProtectedPhase;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.OtpChallenge;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.OtpChannel;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.OtpScenario;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.OtpStatus;
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryStatus;
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
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort;
import com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordAuthFlowContextProtectorPort;
import com.bct.ngtpa.apiservice.application.port.out.OtpChallengeStatePort;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.ForgotPasswordOtpBindingCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.ForgotPasswordOtpBindingResult;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Branch-oriented reactive tests for {@link ForgotPasswordOtpSessionBindingAdapter}. All
 * collaborator/IO type shapes are verified verbatim (rounds 07-24, 28-29); no assumptions.
 */
class ForgotPasswordOtpSessionBindingAdapterTest {

  private static final Instant NOW = Instant.parse("2026-09-14T00:00:00Z");
  private static final String CONTEXT_CIPHERTEXT_KEY = "forgotPasswordContextCiphertext";
  private static final String CONTEXT_KEY_VERSION_KEY = "forgotPasswordContextKeyVersion";
  private static final String CONTEXT_ALGORITHM_KEY = "forgotPasswordContextAlgorithm";

  private final AuthFlowSessionPort sessions = mock(AuthFlowSessionPort.class);
  private final OtpChallengeStatePort challenges = mock(OtpChallengeStatePort.class);
  private final ForgotPasswordAuthFlowContextProtectorPort protector =
      mock(ForgotPasswordAuthFlowContextProtectorPort.class);

  private final AuthFlowSessionId sid = new AuthFlowSessionId("afs-bind-1");
  private final SessionLockHandle acquiredLock =
      new SessionLockHandle(true, "lock-key", "lock-token");

  private ForgotPasswordOtpSessionBindingAdapter newAdapter() {
    return new ForgotPasswordOtpSessionBindingAdapter(sessions, challenges, protector);
  }

  // ----- fixtures ------------------------------------------------------------

  private AuthFlowSession sessionWith(
      AuthFlowScenario scenario, AuthFlowStatus status, Instant expiresAt) {
    return new AuthFlowSession(
        sid,
        scenario,
        status,
        "csrfhash",
        null,
        new AuthFlowSessionData(
            "email",
            "j***@example.com",
            Map.of(
                CONTEXT_CIPHERTEXT_KEY, "ct-1",
                CONTEXT_KEY_VERSION_KEY, "kv-1",
                CONTEXT_ALGORITHM_KEY, ForgotPasswordAuthFlowEncryptedContext.DEFAULT_ALGORITHM)),
        NOW.minusSeconds(120),
        NOW.minusSeconds(60),
        expiresAt);
  }

  private AuthFlowSession identityVerifiedSession() {
    return sessionWith(
        AuthFlowScenario.FORGOT_PASSWORD, AuthFlowStatus.IDENTITY_VERIFIED, NOW.plusSeconds(1800));
  }

  private AuthFlowSessionSnapshot snapshotOf(AuthFlowSession session) {
    return new AuthFlowSessionSnapshot(session, new SessionRevision("0".repeat(32)));
  }

  private ForgotPasswordAuthFlowContext contextAtPhase(ForgotPasswordProtectedPhase phase) {
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
        phase);
  }

  private ForgotPasswordOtpBindingCommand command(MessageDeliveryStatus status) {
    return new ForgotPasswordOtpBindingCommand(
        sid,
        acquiredLock,
        status,
        "challenge-1",
        "email",
        "john@example.com",
        "j***@example.com",
        NOW.plusSeconds(300),
        NOW.plusSeconds(60),
        NOW,
        null,
        null,
        null);
  }

  private OtpChallenge validChallenge() {
    return new OtpChallenge(
        "challenge-1",
        OtpScenario.FORGOT_PASSWORD,
        sid.value(),
        OtpChannel.EMAIL,
        "destinationhash",
        "codehash",
        OtpStatus.CREATED,
        0,
        5,
        0,
        5,
        NOW.minusSeconds(120),
        NOW.plusSeconds(300),
        NOW.minusSeconds(120),
        null,
        null);
  }

  private void givenSession(AuthFlowSession session) {
    when(sessions.loadSnapshot(any())).thenReturn(Mono.just(Optional.of(snapshotOf(session))));
  }

  private void givenContext(ForgotPasswordProtectedPhase phase) {
    when(protector.unprotect(any())).thenReturn(contextAtPhase(phase));
  }

  private void givenWriterSucceeds() {
    when(protector.protect(any()))
        .thenReturn(new ForgotPasswordAuthFlowEncryptedContext("ct-2", "kv-2", "AES_GCM_V1"));
    when(sessions.updateRevisioned(any()))
        .thenAnswer(
            invocation ->
                Mono.just(
                    new AuthFlowSessionMutationResult(
                        AuthFlowSessionMutationOutcome.APPLIED,
                        snapshotOf(identityVerifiedSession()))));
  }

  // ----- tests: guard on command ---------------------------------------------

  @Test
  void nullCommandYieldsRejected() {
    StepVerifier.create(newAdapter().apply(null))
        .assertNext(
            r -> assertThat(r.status()).isEqualTo(ForgotPasswordOtpBindingResult.Status.REJECTED))
        .verifyComplete();
  }

  @Test
  void lockNotAcquiredYieldsRejected() {
    ForgotPasswordOtpBindingCommand cmd =
        new ForgotPasswordOtpBindingCommand(
            sid,
            SessionLockHandle.notAcquired(),
            MessageDeliveryStatus.ACCEPTED,
            "challenge-1",
            "email",
            "john@example.com",
            "j***@example.com",
            NOW.plusSeconds(300),
            NOW.plusSeconds(60),
            NOW,
            null,
            null,
            null);
    StepVerifier.create(newAdapter().apply(cmd))
        .assertNext(
            r -> assertThat(r.status()).isEqualTo(ForgotPasswordOtpBindingResult.Status.REJECTED))
        .verifyComplete();
  }

  // ----- tests: session gate --------------------------------------------------

  @Test
  void sessionMissingYieldsRejected() {
    when(sessions.loadSnapshot(any())).thenReturn(Mono.just(Optional.empty()));
    StepVerifier.create(newAdapter().apply(command(MessageDeliveryStatus.ACCEPTED)))
        .assertNext(
            r -> assertThat(r.status()).isEqualTo(ForgotPasswordOtpBindingResult.Status.REJECTED))
        .verifyComplete();
  }

  @Test
  void sessionLoadErrorYieldsRejected() {
    when(sessions.loadSnapshot(any())).thenReturn(Mono.error(new IllegalStateException("boom")));
    StepVerifier.create(newAdapter().apply(command(MessageDeliveryStatus.ACCEPTED)))
        .assertNext(
            r -> assertThat(r.status()).isEqualTo(ForgotPasswordOtpBindingResult.Status.REJECTED))
        .verifyComplete();
  }

  @Test
  void wrongScenarioYieldsRejected() {
    givenSession(
        sessionWith(
            AuthFlowScenario.LOGIN, AuthFlowStatus.IDENTITY_VERIFIED, NOW.plusSeconds(1800)));
    StepVerifier.create(newAdapter().apply(command(MessageDeliveryStatus.ACCEPTED)))
        .assertNext(
            r -> assertThat(r.status()).isEqualTo(ForgotPasswordOtpBindingResult.Status.REJECTED))
        .verifyComplete();
  }

  @Test
  void wrongStatusYieldsRejected() {
    givenSession(
        sessionWith(
            AuthFlowScenario.FORGOT_PASSWORD, AuthFlowStatus.OTP_SENT, NOW.plusSeconds(1800)));
    StepVerifier.create(newAdapter().apply(command(MessageDeliveryStatus.ACCEPTED)))
        .assertNext(
            r -> assertThat(r.status()).isEqualTo(ForgotPasswordOtpBindingResult.Status.REJECTED))
        .verifyComplete();
  }

  @Test
  void expiredSessionYieldsRejected() {
    givenSession(
        sessionWith(
            AuthFlowScenario.FORGOT_PASSWORD,
            AuthFlowStatus.IDENTITY_VERIFIED,
            NOW.minusSeconds(1)));
    StepVerifier.create(newAdapter().apply(command(MessageDeliveryStatus.ACCEPTED)))
        .assertNext(
            r -> assertThat(r.status()).isEqualTo(ForgotPasswordOtpBindingResult.Status.REJECTED))
        .verifyComplete();
  }

  // ----- tests: phase gate -----------------------------------------------------

  @Test
  void wrongPhaseYieldsRejected() {
    givenSession(identityVerifiedSession());
    givenContext(ForgotPasswordProtectedPhase.OTP_SENT);
    StepVerifier.create(newAdapter().apply(command(MessageDeliveryStatus.ACCEPTED)))
        .assertNext(
            r -> assertThat(r.status()).isEqualTo(ForgotPasswordOtpBindingResult.Status.REJECTED))
        .verifyComplete();
  }

  // ----- tests: delivery status branches --------------------------------------

  @Test
  void failedDeliveryYieldsConfirmedFailed() {
    givenSession(identityVerifiedSession());
    givenContext(ForgotPasswordProtectedPhase.IDENTITY_VERIFIED);
    StepVerifier.create(newAdapter().apply(command(MessageDeliveryStatus.FAILED)))
        .assertNext(
            r ->
                assertThat(r.status())
                    .isEqualTo(ForgotPasswordOtpBindingResult.Status.CONFIRMED_FAILED))
        .verifyComplete();
  }

  @Test
  void unknownOutcomeDeliveryYieldsUnknownOutcome() {
    givenSession(identityVerifiedSession());
    givenContext(ForgotPasswordProtectedPhase.IDENTITY_VERIFIED);
    givenWriterSucceeds();
    StepVerifier.create(newAdapter().apply(command(MessageDeliveryStatus.UNKNOWN_OUTCOME)))
        .assertNext(
            r ->
                assertThat(r.status())
                    .isEqualTo(ForgotPasswordOtpBindingResult.Status.UNKNOWN_OUTCOME))
        .verifyComplete();
  }

  // ----- tests: ACCEPTED - field guards ----------------------------------------

  @Test
  void acceptedWithBlankChallengeIdYieldsRejected() {
    givenSession(identityVerifiedSession());
    givenContext(ForgotPasswordProtectedPhase.IDENTITY_VERIFIED);
    ForgotPasswordOtpBindingCommand cmd =
        new ForgotPasswordOtpBindingCommand(
            sid,
            acquiredLock,
            MessageDeliveryStatus.ACCEPTED,
            "",
            "email",
            "john@example.com",
            "j***@example.com",
            NOW.plusSeconds(300),
            NOW.plusSeconds(60),
            NOW,
            null,
            null,
            null);
    StepVerifier.create(newAdapter().apply(cmd))
        .assertNext(
            r -> assertThat(r.status()).isEqualTo(ForgotPasswordOtpBindingResult.Status.REJECTED))
        .verifyComplete();
  }

  // ----- tests: ACCEPTED - challenge lookup/binding ----------------------------

  @Test
  void acceptedChallengeNotFoundYieldsRejected() {
    givenSession(identityVerifiedSession());
    givenContext(ForgotPasswordProtectedPhase.IDENTITY_VERIFIED);
    when(challenges.findById(any())).thenReturn(Mono.just(Optional.empty()));
    StepVerifier.create(newAdapter().apply(command(MessageDeliveryStatus.ACCEPTED)))
        .assertNext(
            r -> assertThat(r.status()).isEqualTo(ForgotPasswordOtpBindingResult.Status.REJECTED))
        .verifyComplete();
  }

  @Test
  void acceptedChallengeWrongScenarioYieldsRejected() {
    givenSession(identityVerifiedSession());
    givenContext(ForgotPasswordProtectedPhase.IDENTITY_VERIFIED);
    OtpChallenge wrong =
        new OtpChallenge(
            "challenge-1",
            OtpScenario.LOGIN,
            sid.value(),
            OtpChannel.EMAIL,
            "destinationhash",
            "codehash",
            OtpStatus.CREATED,
            0,
            5,
            0,
            5,
            NOW.minusSeconds(120),
            NOW.plusSeconds(300),
            NOW.minusSeconds(120),
            null,
            null);
    when(challenges.findById(any())).thenReturn(Mono.just(Optional.of(wrong)));
    StepVerifier.create(newAdapter().apply(command(MessageDeliveryStatus.ACCEPTED)))
        .assertNext(
            r -> assertThat(r.status()).isEqualTo(ForgotPasswordOtpBindingResult.Status.REJECTED))
        .verifyComplete();
  }

  @Test
  void acceptedChallengeExpiryMismatchYieldsRejected() {
    givenSession(identityVerifiedSession());
    givenContext(ForgotPasswordProtectedPhase.IDENTITY_VERIFIED);
    OtpChallenge mismatchedExpiry =
        new OtpChallenge(
            "challenge-1",
            OtpScenario.FORGOT_PASSWORD,
            sid.value(),
            OtpChannel.EMAIL,
            "destinationhash",
            "codehash",
            OtpStatus.CREATED,
            0,
            5,
            0,
            5,
            NOW.minusSeconds(120),
            NOW.plusSeconds(999),
            NOW.minusSeconds(120),
            null,
            null);
    when(challenges.findById(any())).thenReturn(Mono.just(Optional.of(mismatchedExpiry)));
    StepVerifier.create(newAdapter().apply(command(MessageDeliveryStatus.ACCEPTED)))
        .assertNext(
            r -> assertThat(r.status()).isEqualTo(ForgotPasswordOtpBindingResult.Status.REJECTED))
        .verifyComplete();
  }

  @Test
  void acceptedChallengeAlreadyExpiredYieldsRejected() {
    givenSession(identityVerifiedSession());
    givenContext(ForgotPasswordProtectedPhase.IDENTITY_VERIFIED);
    OtpChallenge expired =
        new OtpChallenge(
            "challenge-1",
            OtpScenario.FORGOT_PASSWORD,
            sid.value(),
            OtpChannel.EMAIL,
            "destinationhash",
            "codehash",
            OtpStatus.CREATED,
            0,
            5,
            0,
            5,
            NOW.minusSeconds(500),
            NOW.minusSeconds(1),
            NOW.minusSeconds(500),
            null,
            null);
    ForgotPasswordOtpBindingCommand cmd =
        new ForgotPasswordOtpBindingCommand(
            sid,
            acquiredLock,
            MessageDeliveryStatus.ACCEPTED,
            "challenge-1",
            "email",
            "john@example.com",
            "j***@example.com",
            NOW.minusSeconds(1),
            NOW.plusSeconds(60),
            NOW,
            null,
            null,
            null);
    when(challenges.findById(any())).thenReturn(Mono.just(Optional.of(expired)));
    StepVerifier.create(newAdapter().apply(cmd))
        .assertNext(
            r -> assertThat(r.status()).isEqualTo(ForgotPasswordOtpBindingResult.Status.REJECTED))
        .verifyComplete();
  }

  // ----- tests: ACCEPTED happy path --------------------------------------------

  @Test
  void acceptedWithValidChallengeYieldsBound() {
    givenSession(identityVerifiedSession());
    givenContext(ForgotPasswordProtectedPhase.IDENTITY_VERIFIED);
    givenWriterSucceeds();
    when(challenges.findById(any())).thenReturn(Mono.just(Optional.of(validChallenge())));
    StepVerifier.create(newAdapter().apply(command(MessageDeliveryStatus.ACCEPTED)))
        .assertNext(
            r -> assertThat(r.status()).isEqualTo(ForgotPasswordOtpBindingResult.Status.BOUND))
        .verifyComplete();
  }

  @Test
  void acceptedChallengeLookupErrorYieldsRejected() {
    givenSession(identityVerifiedSession());
    givenContext(ForgotPasswordProtectedPhase.IDENTITY_VERIFIED);
    when(challenges.findById(any())).thenReturn(Mono.error(new IllegalStateException("boom")));
    StepVerifier.create(newAdapter().apply(command(MessageDeliveryStatus.ACCEPTED)))
        .assertNext(
            r -> assertThat(r.status()).isEqualTo(ForgotPasswordOtpBindingResult.Status.REJECTED))
        .verifyComplete();
  }

  // ----- tests: ACCEPTED - trust and scheme fields (scripts 180-182) ------------------------

  private ForgotPasswordOtpBindingCommand acceptedCommandWith(
      String trustCode, String schemeName, String schemeNameChi) {
    return new ForgotPasswordOtpBindingCommand(
        sid,
        acquiredLock,
        MessageDeliveryStatus.ACCEPTED,
        "challenge-1",
        "email",
        "john@example.com",
        "j***@example.com",
        NOW.plusSeconds(300),
        NOW.plusSeconds(60),
        NOW,
        trustCode,
        schemeName,
        schemeNameChi);
  }

  private ForgotPasswordAuthFlowContext storedContextWithTrustFields() {
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
        "STORED-TRUST",
        "Stored Scheme",
        "Stored Scheme Chi",
        "v1",
        ForgotPasswordProtectedPhase.IDENTITY_VERIFIED);
  }

  private ForgotPasswordAuthFlowContext bindAndCapturePersistedContext(
      ForgotPasswordOtpBindingCommand command) {
    givenSession(identityVerifiedSession());
    givenWriterSucceeds();
    when(challenges.findById(any())).thenReturn(Mono.just(Optional.of(validChallenge())));
    StepVerifier.create(newAdapter().apply(command))
        .assertNext(
            r -> assertThat(r.status()).isEqualTo(ForgotPasswordOtpBindingResult.Status.BOUND))
        .verifyComplete();
    ArgumentCaptor<ForgotPasswordAuthFlowContext> persisted =
        ArgumentCaptor.forClass(ForgotPasswordAuthFlowContext.class);
    verify(protector).protect(persisted.capture());
    return persisted.getValue();
  }

  @Test
  void acceptedPersistsTheTrustFieldsCarriedByTheCommand() {
    when(protector.unprotect(any()))
        .thenReturn(contextAtPhase(ForgotPasswordProtectedPhase.IDENTITY_VERIFIED));

    ForgotPasswordAuthFlowContext persisted =
        bindAndCapturePersistedContext(acceptedCommandWith("HSBC", "Scheme En", "Scheme Chi"));

    assertThat(persisted.phase()).isEqualTo(ForgotPasswordProtectedPhase.OTP_SENT);
    assertThat(persisted.trustCode()).isEqualTo("HSBC");
    assertThat(persisted.schemeName()).isEqualTo("Scheme En");
    assertThat(persisted.schemeNameChi()).isEqualTo("Scheme Chi");
  }

  @Test
  void acceptedKeepsTheStoredTrustFieldsWhenTheCommandCarriesNone() {
    when(protector.unprotect(any())).thenReturn(storedContextWithTrustFields());

    ForgotPasswordAuthFlowContext persisted =
        bindAndCapturePersistedContext(acceptedCommandWith(null, null, null));

    assertThat(persisted.phase()).isEqualTo(ForgotPasswordProtectedPhase.OTP_SENT);
    assertThat(persisted.trustCode()).isEqualTo("STORED-TRUST");
    assertThat(persisted.schemeName()).isEqualTo("Stored Scheme");
    assertThat(persisted.schemeNameChi()).isEqualTo("Stored Scheme Chi");
  }

  @Test
  void acceptedTreatsBlankCommandTrustFieldsAsAbsent() {
    when(protector.unprotect(any())).thenReturn(storedContextWithTrustFields());

    ForgotPasswordAuthFlowContext persisted =
        bindAndCapturePersistedContext(acceptedCommandWith(" ", "", "  "));

    assertThat(persisted.trustCode()).isEqualTo("STORED-TRUST");
    assertThat(persisted.schemeName()).isEqualTo("Stored Scheme");
    assertThat(persisted.schemeNameChi()).isEqualTo("Stored Scheme Chi");
  }
}
