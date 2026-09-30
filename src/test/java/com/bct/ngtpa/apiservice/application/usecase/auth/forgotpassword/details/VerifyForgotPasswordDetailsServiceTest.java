package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.details;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowContext;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowEncryptedContext;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordProtectedPhase;
import com.bct.ngtpa.apiservice.application.dto.session.SessionLockHandle;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowScenario;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionData;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowStatus;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionMutationLockPort;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort;
import com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordAuthFlowContextProtectorPort;
import com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordDetailsLookupPort;
import com.bct.ngtpa.apiservice.application.service.auth.forgotpassword.context.ForgotPasswordProtectedAuthFlowContextLoader;
import com.bct.ngtpa.apiservice.application.service.auth.forgotpassword.context.ForgotPasswordProtectedAuthFlowContextResult;
import com.bct.ngtpa.apiservice.application.service.auth.forgotpassword.context.ForgotPasswordProtectedAuthFlowContextStatus;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.AuthFlowSessionLifecycleService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Branch-oriented reactive tests for {@link VerifyForgotPasswordDetailsService}. Note: this service
 * uses {@code sessions.load(...)} (bare {@code AuthFlowSession}), NOT {@code loadSnapshot(...)}
 * used elsewhere - confirmed verbatim from {@code AuthFlowSessionPort}. {@code lifecycleService} is
 * a mocked collaborator; its internals are not re-tested here.
 */
class VerifyForgotPasswordDetailsServiceTest {

  private static final Instant NOW = Instant.parse("2026-09-14T00:00:00Z");
  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

  private final AuthFlowSessionPort sessions = mock(AuthFlowSessionPort.class);
  private final AuthFlowSessionMutationLockPort locks = mock(AuthFlowSessionMutationLockPort.class);
  private final ForgotPasswordProtectedAuthFlowContextLoader loader =
      mock(ForgotPasswordProtectedAuthFlowContextLoader.class);
  private final ForgotPasswordDetailsLookupPort lookup =
      mock(ForgotPasswordDetailsLookupPort.class);
  private final AuthFlowSessionLifecycleService lifecycleService =
      mock(AuthFlowSessionLifecycleService.class);
  private final ForgotPasswordAuthFlowContextProtectorPort protector =
      mock(ForgotPasswordAuthFlowContextProtectorPort.class);

  private final AuthFlowSessionId sid = new AuthFlowSessionId("afs-details-1");

  private VerifyForgotPasswordDetailsService newService() {
    return new VerifyForgotPasswordDetailsService(
        sessions, locks, loader, lookup, lifecycleService, protector, clock);
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

  private AuthFlowSession identityVerifiedSession() {
    return sessionWith(
        AuthFlowScenario.FORGOT_PASSWORD, AuthFlowStatus.IDENTITY_VERIFIED, NOW.plusSeconds(1800));
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
        "v1",
        phase);
  }

  private ForgotPasswordDetailsCommand validCommand() {
    return new ForgotPasswordDetailsCommand(
        sid,
        "csrf-token",
        "https://portal.bcthk.com",
        "https://portal.bcthk.com/details",
        "req-1",
        "01/01/1990",
        "test@example.com",
        null,
        Duration.ofSeconds(10));
  }

  private void givenLockAcquired() {
    when(locks.acquire(any(), any()))
        .thenReturn(Mono.just(new SessionLockHandle(true, "lock-key", "lock-token")));
    when(locks.release(any())).thenReturn(Mono.just(true));
  }

  private void givenSession(AuthFlowSession session) {
    when(sessions.load(any())).thenReturn(Mono.just(Optional.of(session)));
  }

  private void givenLoaderValid(ForgotPasswordProtectedPhase phase) {
    when(loader.load(any()))
        .thenReturn(
            Mono.just(
                ForgotPasswordProtectedAuthFlowContextResult.valid(
                    "challenge-1", contextAtPhase(phase))));
  }

  private ForgotPasswordDetailsLookupResult matchedWithAccountEnv(String accountEnv) {
    return new ForgotPasswordDetailsLookupResult(
        ForgotPasswordDetailsLookupStatus.MATCHED, accountEnv);
  }

  // ----- guards ------------------------------------------------------------

  @Test
  void nullCommandYieldsAuthFlowInvalid() {
    StepVerifier.create(newService().verify(null))
        .assertNext(
            r -> assertThat(r.status()).isEqualTo(ForgotPasswordDetailsStatus.INVALID_AUTH_FLOW))
        .verifyComplete();
  }

  @Test
  void invalidDateOfBirthYieldsValidationFailed() {
    ForgotPasswordDetailsCommand cmd =
        new ForgotPasswordDetailsCommand(
            sid, "csrf", "o", "r", "req-1", "31/02/1990", "test@example.com", null, null);
    StepVerifier.create(newService().verify(cmd))
        .assertNext(
            r -> {
              assertThat(r.status()).isEqualTo(ForgotPasswordDetailsStatus.VALIDATION_FAILED);
              assertThat(r.targets()).contains("dateOfBirth");
            })
        .verifyComplete();
  }

  @Test
  void bothEmailAndMobileBlankYieldsValidationFailed() {
    ForgotPasswordDetailsCommand cmd =
        new ForgotPasswordDetailsCommand(
            sid, "csrf", "o", "r", "req-1", "01/01/1990", null, null, null);
    StepVerifier.create(newService().verify(cmd))
        .assertNext(
            r -> assertThat(r.status()).isEqualTo(ForgotPasswordDetailsStatus.VALIDATION_FAILED))
        .verifyComplete();
  }

  @Test
  void lockNotAcquiredYieldsAuthFlowInvalid() {
    when(locks.acquire(any(), any())).thenReturn(Mono.just(SessionLockHandle.notAcquired()));
    StepVerifier.create(newService().verify(validCommand()))
        .assertNext(
            r -> assertThat(r.status()).isEqualTo(ForgotPasswordDetailsStatus.INVALID_AUTH_FLOW))
        .verifyComplete();
  }

  // ----- session gate --------------------------------------------------------

  @Test
  void sessionMissingYieldsAuthFlowInvalid() {
    givenLockAcquired();
    when(sessions.load(any())).thenReturn(Mono.just(Optional.empty()));
    StepVerifier.create(newService().verify(validCommand()))
        .assertNext(
            r -> assertThat(r.status()).isEqualTo(ForgotPasswordDetailsStatus.INVALID_AUTH_FLOW))
        .verifyComplete();
  }

  @Test
  void wrongStatusYieldsAuthFlowInvalid() {
    givenLockAcquired();
    givenSession(
        sessionWith(
            AuthFlowScenario.FORGOT_PASSWORD, AuthFlowStatus.OTP_SENT, NOW.plusSeconds(1800)));
    StepVerifier.create(newService().verify(validCommand()))
        .assertNext(
            r -> assertThat(r.status()).isEqualTo(ForgotPasswordDetailsStatus.INVALID_AUTH_FLOW))
        .verifyComplete();
  }

  @Test
  void expiredSessionYieldsAuthFlowInvalid() {
    givenLockAcquired();
    givenSession(
        sessionWith(
            AuthFlowScenario.FORGOT_PASSWORD,
            AuthFlowStatus.IDENTITY_VERIFIED,
            NOW.minusSeconds(1)));
    StepVerifier.create(newService().verify(validCommand()))
        .assertNext(
            r -> assertThat(r.status()).isEqualTo(ForgotPasswordDetailsStatus.INVALID_AUTH_FLOW))
        .verifyComplete();
  }

  // ----- loader mapping --------------------------------------------------------

  @Test
  void loaderCsrfInvalidYieldsSecurityRejected() {
    givenLockAcquired();
    givenSession(identityVerifiedSession());
    when(loader.load(any()))
        .thenReturn(
            Mono.just(
                ForgotPasswordProtectedAuthFlowContextResult.invalid(
                    ForgotPasswordProtectedAuthFlowContextStatus.CSRF_INVALID)));
    StepVerifier.create(newService().verify(validCommand()))
        .assertNext(
            r -> assertThat(r.status()).isEqualTo(ForgotPasswordDetailsStatus.SECURITY_REJECTED))
        .verifyComplete();
  }

  @Test
  void loaderOtherFailureYieldsAuthFlowInvalid() {
    givenLockAcquired();
    givenSession(identityVerifiedSession());
    when(loader.load(any()))
        .thenReturn(
            Mono.just(
                ForgotPasswordProtectedAuthFlowContextResult.invalid(
                    ForgotPasswordProtectedAuthFlowContextStatus.MISSING_CONTEXT)));
    StepVerifier.create(newService().verify(validCommand()))
        .assertNext(
            r -> assertThat(r.status()).isEqualTo(ForgotPasswordDetailsStatus.INVALID_AUTH_FLOW))
        .verifyComplete();
  }

  @Test
  void wrongPhaseYieldsAuthFlowInvalid() {
    givenLockAcquired();
    givenSession(identityVerifiedSession());
    givenLoaderValid(ForgotPasswordProtectedPhase.OTP_SENT);
    StepVerifier.create(newService().verify(validCommand()))
        .assertNext(
            r -> assertThat(r.status()).isEqualTo(ForgotPasswordDetailsStatus.INVALID_AUTH_FLOW))
        .verifyComplete();
  }

  // ----- lookup outcomes --------------------------------------------------------

  @Test
  void lookupMatchedWithBlankAccountEnvYieldsDetailsVerified() {
    givenLockAcquired();
    givenSession(identityVerifiedSession());
    givenLoaderValid(ForgotPasswordProtectedPhase.IDENTITY_VERIFIED);
    when(lookup.verify(any())).thenReturn(Mono.just(matchedWithAccountEnv(null)));
    StepVerifier.create(newService().verify(validCommand()))
        .assertNext(
            r -> assertThat(r.status()).isEqualTo(ForgotPasswordDetailsStatus.DETAILS_VERIFIED))
        .verifyComplete();
  }

  @Test
  void lookupNotMatchedYieldsBusinessRejected() {
    givenLockAcquired();
    givenSession(identityVerifiedSession());
    givenLoaderValid(ForgotPasswordProtectedPhase.IDENTITY_VERIFIED);
    when(lookup.verify(any()))
        .thenReturn(
            Mono.just(
                new ForgotPasswordDetailsLookupResult(
                    ForgotPasswordDetailsLookupStatus.NOT_FOUND)));
    StepVerifier.create(newService().verify(validCommand()))
        .assertNext(
            r -> assertThat(r.status()).isEqualTo(ForgotPasswordDetailsStatus.BUSINESS_REJECTED))
        .verifyComplete();
  }

  @Test
  void lookupMatchedWithAccountEnvAndLifecycleSuccessYieldsDetailsVerified() {
    givenLockAcquired();
    givenSession(identityVerifiedSession());
    givenLoaderValid(ForgotPasswordProtectedPhase.IDENTITY_VERIFIED);
    when(lookup.verify(any())).thenReturn(Mono.just(matchedWithAccountEnv("env1")));
    when(protector.protect(any()))
        .thenReturn(new ForgotPasswordAuthFlowEncryptedContext("ct", "kv", "AES_GCM_V1"));
    when(lifecycleService.updateAttributes(any())).thenReturn(Mono.just(identityVerifiedSession()));
    StepVerifier.create(newService().verify(validCommand()))
        .assertNext(
            r -> assertThat(r.status()).isEqualTo(ForgotPasswordDetailsStatus.DETAILS_VERIFIED))
        .verifyComplete();
  }

  @Test
  void lookupMatchedWithAccountEnvAndLifecycleErrorYieldsSystemFailed() {
    givenLockAcquired();
    givenSession(identityVerifiedSession());
    givenLoaderValid(ForgotPasswordProtectedPhase.IDENTITY_VERIFIED);
    when(lookup.verify(any())).thenReturn(Mono.just(matchedWithAccountEnv("env1")));
    when(protector.protect(any()))
        .thenReturn(new ForgotPasswordAuthFlowEncryptedContext("ct", "kv", "AES_GCM_V1"));
    when(lifecycleService.updateAttributes(any()))
        .thenReturn(Mono.error(new IllegalStateException("boom")));
    StepVerifier.create(newService().verify(validCommand()))
        .assertNext(
            r -> assertThat(r.status()).isEqualTo(ForgotPasswordDetailsStatus.SYSTEM_FAILED))
        .verifyComplete();
  }

  @Test
  void loadErrorYieldsSystemFailed() {
    givenLockAcquired();
    when(sessions.load(any())).thenReturn(Mono.error(new IllegalStateException("boom")));
    StepVerifier.create(newService().verify(validCommand()))
        .assertNext(
            r -> assertThat(r.status()).isEqualTo(ForgotPasswordDetailsStatus.SYSTEM_FAILED))
        .verifyComplete();
  }
}
