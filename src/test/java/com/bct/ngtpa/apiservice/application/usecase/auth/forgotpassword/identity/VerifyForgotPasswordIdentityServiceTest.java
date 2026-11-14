package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowContext;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowEncryptedContext;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordProtectedPhase;
import com.bct.ngtpa.apiservice.application.dto.observability.FailureEvent;
import com.bct.ngtpa.apiservice.application.dto.observability.FailureOrigin;
import com.bct.ngtpa.apiservice.application.dto.session.SessionLockHandle;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowScenario;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionData;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionMutationOutcome;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionMutationResult;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionSnapshot;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowStatus;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.RevisionedAuthFlowSessionMutationCommand;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.SessionRevision;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionExpiryRefreshPort;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionMutationLockPort;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort;
import com.bct.ngtpa.apiservice.application.port.out.FailureEventPort;
import com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordAuthFlowContextProtectorPort;
import com.bct.ngtpa.apiservice.application.service.auth.forgotpassword.context.ForgotPasswordProtectedAuthFlowContextLoader;
import com.bct.ngtpa.apiservice.application.service.auth.forgotpassword.context.ForgotPasswordProtectedAuthFlowContextResult;
import com.bct.ngtpa.apiservice.application.service.auth.forgotpassword.context.ForgotPasswordProtectedAuthFlowContextStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Branch-oriented reactive tests for {@link VerifyForgotPasswordIdentityService}. Uses the standard
 * loadSnapshot -> loader -> RevisionAwareAuthFlowSessionWriter pattern (rounds 09/11/25). {@code
 * expiry} (AuthFlowSessionExpiryRefreshPort) is a constructed-but-unused field in the real
 * production source (confirmed) - wired but never stubbed.
 */
class VerifyForgotPasswordIdentityServiceTest {

  private static final Instant NOW = Instant.parse("2026-09-14T00:00:00Z");
  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
  private final Duration lease = Duration.ofSeconds(30);

  private final ForgotPasswordProtectedAuthFlowContextLoader loader =
      mock(ForgotPasswordProtectedAuthFlowContextLoader.class);
  private final ForgotPasswordAuthFlowContextProtectorPort protector =
      mock(ForgotPasswordAuthFlowContextProtectorPort.class);
  private final AuthFlowSessionPort sessions = mock(AuthFlowSessionPort.class);
  private final AuthFlowSessionMutationLockPort locks = mock(AuthFlowSessionMutationLockPort.class);
  private final AuthFlowSessionExpiryRefreshPort expiry =
      mock(AuthFlowSessionExpiryRefreshPort.class);
  private final RecordingFailureEventPort failureEvents = new RecordingFailureEventPort();

  private final AuthFlowSessionId sid = new AuthFlowSessionId("afs-identity-1");

  private VerifyForgotPasswordIdentityService newService() {
    return new VerifyForgotPasswordIdentityService(
        loader, protector, sessions, locks, expiry, clock, lease, failureEvents);
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
        new AuthFlowSessionData("email", "j***@example.com", Map.of()),
        NOW.minusSeconds(120),
        NOW.minusSeconds(60),
        expiresAt);
  }

  private AuthFlowSession startedSession() {
    return sessionWith(
        AuthFlowScenario.FORGOT_PASSWORD, AuthFlowStatus.STARTED, NOW.plusSeconds(1800));
  }

  private AuthFlowSessionSnapshot snapshotOf(AuthFlowSession session) {
    return new AuthFlowSessionSnapshot(session, new SessionRevision("0".repeat(32)));
  }

  private ForgotPasswordAuthFlowContext contextAtPhase(
      ForgotPasswordProtectedPhase phase, String identityType, String identityValue) {
    return new ForgotPasswordAuthFlowContext(
        "john",
        identityValue,
        identityType,
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

  private VerifyForgotPasswordIdentityCommand command(String identityType, String identityValue) {
    return command(identityType, identityValue, "req-1");
  }

  private VerifyForgotPasswordIdentityCommand command(
      String identityType, String identityValue, String requestId) {
    return new VerifyForgotPasswordIdentityCommand(
        sid,
        "csrf-token",
        "https://portal.bcthk.com",
        "https://portal.bcthk.com/identity",
        requestId,
        identityType,
        identityValue);
  }

  private VerifyForgotPasswordIdentityCommand validCommand() {
    return command("GPN", "G12345678");
  }

  private void givenLockAcquired() {
    when(locks.acquire(any(), any()))
        .thenReturn(Mono.just(new SessionLockHandle(true, "lock-key", "lock-token")));
    when(locks.release(any())).thenReturn(Mono.just(true));
  }

  private void givenSession(AuthFlowSession session) {
    when(sessions.loadSnapshot(any())).thenReturn(Mono.just(Optional.of(snapshotOf(session))));
  }

  private void givenLoaderValid(
      ForgotPasswordProtectedPhase phase, String identityType, String identityValue) {
    when(loader.load(any()))
        .thenReturn(
            Mono.just(
                ForgotPasswordProtectedAuthFlowContextResult.valid(
                    "challenge-1", contextAtPhase(phase, identityType, identityValue))));
  }

  private void givenWriterSucceeds() {
    when(protector.protect(any()))
        .thenReturn(new ForgotPasswordAuthFlowEncryptedContext("ct", "kv", "AES_GCM_V1"));
    when(sessions.updateRevisioned(any()))
        .thenAnswer(
            invocation ->
                Mono.just(
                    new AuthFlowSessionMutationResult(
                        AuthFlowSessionMutationOutcome.APPLIED, snapshotOf(startedSession()))));
  }

  // ----- guards ------------------------------------------------------------

  @Test
  void nullCommandYieldsInvalidAuthFlow() {
    StepVerifier.create(newService().verify(null))
        .assertNext(
            r ->
                assertThat(r.status())
                    .isEqualTo(VerifyForgotPasswordIdentityStatus.INVALID_AUTH_FLOW))
        .verifyComplete();
  }

  @Test
  void unrecognizedIdentityTypeYieldsInvalidType() {
    StepVerifier.create(newService().verify(command("UNKNOWN_TYPE", "value")))
        .assertNext(
            r -> assertThat(r.status()).isEqualTo(VerifyForgotPasswordIdentityStatus.INVALID_TYPE))
        .verifyComplete();
    assertThat(failureEvents.events).isEmpty();
  }

  @Test
  void lockNotAcquiredYieldsInvalidAuthFlow() {
    when(locks.acquire(any(), any())).thenReturn(Mono.just(SessionLockHandle.notAcquired()));
    StepVerifier.create(newService().verify(validCommand()))
        .assertNext(
            r ->
                assertThat(r.status())
                    .isEqualTo(VerifyForgotPasswordIdentityStatus.INVALID_AUTH_FLOW))
        .verifyComplete();
    assertThat(failureEvents.events).isEmpty();
  }

  @Test
  void lockAcquisitionExceptionPublishesOneCauseFreeLocalSystemIncident() {
    when(locks.acquire(any(), any()))
        .thenReturn(Mono.error(new IllegalStateException("identityValue=LOCK-SECRET")));

    StepVerifier.create(newService().verify(command("GPN", "G12345678", "req-1\nunsafe")))
        .assertNext(
            r ->
                assertThat(r.status()).isEqualTo(VerifyForgotPasswordIdentityStatus.SYSTEM_FAILED))
        .verifyComplete();

    assertSingleTechnicalFailure("session-lock-acquire", "req-1?unsafe");
  }

  // ----- initial session gate --------------------------------------------------

  @Test
  void sessionMissingYieldsInvalidAuthFlow() {
    givenLockAcquired();
    when(sessions.loadSnapshot(any())).thenReturn(Mono.just(Optional.empty()));
    StepVerifier.create(newService().verify(validCommand()))
        .assertNext(
            r ->
                assertThat(r.status())
                    .isEqualTo(VerifyForgotPasswordIdentityStatus.INVALID_AUTH_FLOW))
        .verifyComplete();
    assertThat(failureEvents.events).isEmpty();
  }

  @Test
  void wrongStatusYieldsInvalidAuthFlow() {
    givenLockAcquired();
    givenSession(
        sessionWith(
            AuthFlowScenario.FORGOT_PASSWORD, AuthFlowStatus.OTP_SENT, NOW.plusSeconds(1800)));
    StepVerifier.create(newService().verify(validCommand()))
        .assertNext(
            r ->
                assertThat(r.status())
                    .isEqualTo(VerifyForgotPasswordIdentityStatus.INVALID_AUTH_FLOW))
        .verifyComplete();
  }

  @Test
  void expiredSessionYieldsInvalidAuthFlow() {
    givenLockAcquired();
    givenSession(
        sessionWith(AuthFlowScenario.FORGOT_PASSWORD, AuthFlowStatus.STARTED, NOW.minusSeconds(1)));
    StepVerifier.create(newService().verify(validCommand()))
        .assertNext(
            r ->
                assertThat(r.status())
                    .isEqualTo(VerifyForgotPasswordIdentityStatus.INVALID_AUTH_FLOW))
        .verifyComplete();
  }

  // ----- loader mapping --------------------------------------------------------

  @Test
  void loaderOriginRefererInvalidYieldsSecurityRejected() {
    givenLockAcquired();
    givenSession(startedSession());
    when(loader.load(any()))
        .thenReturn(
            Mono.just(
                ForgotPasswordProtectedAuthFlowContextResult.invalid(
                    ForgotPasswordProtectedAuthFlowContextStatus.ORIGIN_REFERER_INVALID)));
    StepVerifier.create(newService().verify(validCommand()))
        .assertNext(
            r ->
                assertThat(r.status())
                    .isEqualTo(VerifyForgotPasswordIdentityStatus.SECURITY_REJECTED))
        .verifyComplete();
    assertThat(failureEvents.events).isEmpty();
  }

  @Test
  void loaderOtherFailureYieldsInvalidAuthFlow() {
    givenLockAcquired();
    givenSession(startedSession());
    when(loader.load(any()))
        .thenReturn(
            Mono.just(
                ForgotPasswordProtectedAuthFlowContextResult.invalid(
                    ForgotPasswordProtectedAuthFlowContextStatus.MISSING_CONTEXT)));
    StepVerifier.create(newService().verify(validCommand()))
        .assertNext(
            r ->
                assertThat(r.status())
                    .isEqualTo(VerifyForgotPasswordIdentityStatus.INVALID_AUTH_FLOW))
        .verifyComplete();
    assertThat(failureEvents.events).isEmpty();
  }

  @Test
  void wrongPhaseYieldsInvalidAuthFlow() {
    givenLockAcquired();
    givenSession(startedSession());
    givenLoaderValid(ForgotPasswordProtectedPhase.IDENTITY_VERIFIED, "GPN", "G12345678");
    StepVerifier.create(newService().verify(validCommand()))
        .assertNext(
            r ->
                assertThat(r.status())
                    .isEqualTo(VerifyForgotPasswordIdentityStatus.INVALID_AUTH_FLOW))
        .verifyComplete();
  }

  // ----- identity matching --------------------------------------------------------

  @Test
  void identityMismatchYieldsIncorrectId() {
    givenLockAcquired();
    givenSession(startedSession());
    givenLoaderValid(ForgotPasswordProtectedPhase.USERNAME_VERIFIED, "GPN", "DIFFERENT_VALUE");
    StepVerifier.create(newService().verify(validCommand()))
        .assertNext(
            r -> assertThat(r.status()).isEqualTo(VerifyForgotPasswordIdentityStatus.INCORRECT_ID))
        .verifyComplete();
    assertThat(failureEvents.events).isEmpty();
  }

  @Test
  void identityTypeMismatchYieldsIncorrectId() {
    givenLockAcquired();
    givenSession(startedSession());
    givenLoaderValid(ForgotPasswordProtectedPhase.USERNAME_VERIFIED, "ID", "G12345678");
    StepVerifier.create(newService().verify(validCommand()))
        .assertNext(
            r -> assertThat(r.status()).isEqualTo(VerifyForgotPasswordIdentityStatus.INCORRECT_ID))
        .verifyComplete();
  }

  // ----- re-loaded session gate --------------------------------------------------

  @Test
  void reloadedSessionMissingYieldsInvalidAuthFlow() {
    givenLockAcquired();
    givenLoaderValid(ForgotPasswordProtectedPhase.USERNAME_VERIFIED, "GPN", "G12345678");
    when(sessions.loadSnapshot(any()))
        .thenReturn(Mono.just(Optional.of(snapshotOf(startedSession()))))
        .thenReturn(Mono.just(Optional.empty()));
    StepVerifier.create(newService().verify(validCommand()))
        .assertNext(
            r ->
                assertThat(r.status())
                    .isEqualTo(VerifyForgotPasswordIdentityStatus.INVALID_AUTH_FLOW))
        .verifyComplete();
    assertThat(failureEvents.events).isEmpty();
  }

  // ----- happy path ----------------------------------------------------------------

  @Test
  void matchingIdentityYieldsVerified() {
    givenLockAcquired();
    givenSession(startedSession());
    givenLoaderValid(ForgotPasswordProtectedPhase.USERNAME_VERIFIED, "GPN", "G12345678");
    givenWriterSucceeds();
    StepVerifier.create(newService().verify(validCommand()))
        .assertNext(
            r -> assertThat(r.status()).isEqualTo(VerifyForgotPasswordIdentityStatus.VERIFIED))
        .verifyComplete();
    var transitionCaptor =
        org.mockito.ArgumentCaptor.forClass(RevisionedAuthFlowSessionMutationCommand.class);
    org.mockito.Mockito.verify(sessions).updateRevisioned(transitionCaptor.capture());
    var transition = transitionCaptor.getValue();
    assertThat(transition.expected().session().status()).isEqualTo(AuthFlowStatus.STARTED);
    assertThat(transition.updated().status()).isEqualTo(AuthFlowStatus.IDENTITY_VERIFIED);
    assertThat(failureEvents.events).isEmpty();
  }

  @Test
  void loadSnapshotErrorYieldsSystemFailed() {
    givenLockAcquired();
    when(sessions.loadSnapshot(any()))
        .thenReturn(Mono.error(new IllegalStateException("identityValue=READ-SECRET")));
    StepVerifier.create(newService().verify(validCommand()))
        .assertNext(
            r -> assertThat(r.status()).isEqualTo(VerifyForgotPasswordIdentityStatus.SYSTEM_FAILED))
        .verifyComplete();
    assertSingleTechnicalFailure("session-read", "req-1");
  }

  @Test
  void transitionSnapshotReadExceptionPublishesLocalSystemIncident() {
    givenLockAcquired();
    givenLoaderValid(ForgotPasswordProtectedPhase.USERNAME_VERIFIED, "GPN", "G12345678");
    when(sessions.loadSnapshot(any()))
        .thenReturn(Mono.just(Optional.of(snapshotOf(startedSession()))))
        .thenReturn(Mono.error(new IllegalStateException("token=TRANSITION-READ-SECRET")));

    StepVerifier.create(newService().verify(validCommand()))
        .assertNext(
            r -> assertThat(r.status()).isEqualTo(VerifyForgotPasswordIdentityStatus.SYSTEM_FAILED))
        .verifyComplete();

    assertSingleTechnicalFailure("session-read-before-transition", "req-1");
  }

  @Test
  void protectedContextExceptionPublishesLocalSystemIncident() {
    givenLockAcquired();
    givenSession(startedSession());
    when(loader.load(any()))
        .thenReturn(Mono.error(new IllegalStateException("token=CSRF-SECRET")));

    StepVerifier.create(newService().verify(validCommand()))
        .assertNext(
            r -> assertThat(r.status()).isEqualTo(VerifyForgotPasswordIdentityStatus.SYSTEM_FAILED))
        .verifyComplete();

    assertSingleTechnicalFailure("protected-context-load", "req-1");
  }

  @Test
  void contextProtectionExceptionPublishesLocalSystemIncident() {
    givenLockAcquired();
    givenSession(startedSession());
    givenLoaderValid(ForgotPasswordProtectedPhase.USERNAME_VERIFIED, "GPN", "G12345678");
    when(protector.protect(any()))
        .thenThrow(new IllegalStateException("identityValue=PROTECT-SECRET"));

    StepVerifier.create(newService().verify(validCommand()))
        .assertNext(
            r -> assertThat(r.status()).isEqualTo(VerifyForgotPasswordIdentityStatus.SYSTEM_FAILED))
        .verifyComplete();

    assertSingleTechnicalFailure("context-protection", "req-1");
  }

  @Test
  void revisionConflictPreservesSystemFailureResponseAndPublishesNoIncident() {
    givenLockAcquired();
    givenSession(startedSession());
    givenLoaderValid(ForgotPasswordProtectedPhase.USERNAME_VERIFIED, "GPN", "G12345678");
    givenWriterSucceeds();
    when(sessions.updateRevisioned(any()))
        .thenReturn(
            Mono.just(
                AuthFlowSessionMutationResult.outcome(
                    AuthFlowSessionMutationOutcome.SESSION_REVISION_CHANGED)));

    StepVerifier.create(newService().verify(validCommand()))
        .assertNext(
            r ->
                assertThat(r.status()).isEqualTo(VerifyForgotPasswordIdentityStatus.SYSTEM_FAILED))
        .verifyComplete();

    assertThat(failureEvents.events).isEmpty();
  }

  @Test
  void returnedPersistenceFailurePublishesOneLocalSystemIncident() {
    givenLockAcquired();
    givenSession(startedSession());
    givenLoaderValid(ForgotPasswordProtectedPhase.USERNAME_VERIFIED, "GPN", "G12345678");
    givenWriterSucceeds();
    when(sessions.updateRevisioned(any()))
        .thenReturn(
            Mono.just(
                AuthFlowSessionMutationResult.outcome(AuthFlowSessionMutationOutcome.SYSTEM_FAILURE)));

    StepVerifier.create(newService().verify(validCommand()))
        .assertNext(
            r -> assertThat(r.status()).isEqualTo(VerifyForgotPasswordIdentityStatus.SYSTEM_FAILED))
        .verifyComplete();

    assertSingleTechnicalFailure("session-transition", "req-1");
  }

  @Test
  void thrownRevisionPersistenceFailurePublishesOneLocalSystemIncident() {
    givenLockAcquired();
    givenSession(startedSession());
    givenLoaderValid(ForgotPasswordProtectedPhase.USERNAME_VERIFIED, "GPN", "G12345678");
    givenWriterSucceeds();
    when(sessions.updateRevisioned(any()))
        .thenReturn(Mono.error(new IllegalStateException("password=WRITE-SECRET")));

    StepVerifier.create(newService().verify(validCommand()))
        .assertNext(
            r -> assertThat(r.status()).isEqualTo(VerifyForgotPasswordIdentityStatus.SYSTEM_FAILED))
        .verifyComplete();

    assertSingleTechnicalFailure("session-transition", "req-1");
  }

  private void assertSingleTechnicalFailure(String stage, String requestId) {
    assertThat(failureEvents.events)
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.event()).isEqualTo("forgot-password.identity-check.failed");
              assertThat(event.requestId()).isEqualTo(requestId);
              assertThat(event.capability()).isEqualTo("FORGOT_PASSWORD");
              assertThat(event.operation()).isEqualTo("ForgotPasswordIdentityCheck");
              assertThat(event.origin()).isEqualTo(FailureOrigin.LOCAL_SYSTEM);
              assertThat(event.internalCode()).isEqualTo("IDENTITY_CHECK_TECHNICAL_FAILURE");
              assertThat(event.publicCode()).isEqualTo("err.forgot-password.system.unavailable");
              assertThat(event.httpStatus()).isEqualTo(503);
              assertThat(event.retryable()).isFalse();
              assertThat(event.safeDimensions()).containsExactly(Map.entry("stage", stage));
              assertThat(event.toString())
                  .doesNotContain(
                      "READ-SECRET",
                      "CSRF-SECRET",
                      "PROTECT-SECRET",
                      "WRITE-SECRET",
                      "LOCK-SECRET",
                      "identityValue=",
                      "token=",
                      "password=");
            });
    assertThat(failureEvents.causeCalls).isZero();
  }

  private static final class RecordingFailureEventPort implements FailureEventPort {
    private final List<FailureEvent> events = new ArrayList<>();
    private int causeCalls;

    @Override
    public void publish(FailureEvent event) {
      events.add(event);
    }

    @Override
    public void publish(FailureEvent event, Throwable cause) {
      causeCalls++;
      FailureEventPort.super.publish(event, cause);
    }
  }
}
