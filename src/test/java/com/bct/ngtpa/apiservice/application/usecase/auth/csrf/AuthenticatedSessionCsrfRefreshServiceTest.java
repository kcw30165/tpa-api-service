package com.bct.ngtpa.apiservice.application.usecase.auth.csrf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.application.dto.ActorContext;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.auth.csrf.RefreshCsrfTokenStatus;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSession;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionCsrfRotationResult;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionCsrfRotationStatus;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionStatus;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.ProtectedAuthenticatedSession;
import com.bct.ngtpa.apiservice.application.port.out.AuthenticatedSessionCsrfRotationPort;
import com.bct.ngtpa.apiservice.application.port.out.AuthenticatedSessionPort;
import com.bct.ngtpa.apiservice.application.port.out.AuthenticatedSessionProtectorPort;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.CsrfTokenGenerator;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.CsrfTokenHasher;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/** Branch-oriented reactive tests for {@link AuthenticatedSessionCsrfRefreshService}. */
class AuthenticatedSessionCsrfRefreshServiceTest {

  private static final Instant NOW = Instant.parse("2026-09-14T00:00:00Z");
  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

  private final AuthenticatedSessionPort sessions = mock(AuthenticatedSessionPort.class);
  private final AuthenticatedSessionProtectorPort protector =
      mock(AuthenticatedSessionProtectorPort.class);
  private final AuthenticatedSessionCsrfRotationPort rotations =
      mock(AuthenticatedSessionCsrfRotationPort.class);
  private final CsrfTokenGenerator tokenGenerator = mock(CsrfTokenGenerator.class);
  private final CsrfTokenHasher tokenHasher = mock(CsrfTokenHasher.class);

  private final AuthenticatedSessionId sid = new AuthenticatedSessionId("sid_abcdefghijklmnopqrstuvwx");

  private AuthenticatedSessionCsrfRefreshService newService() {
    return new AuthenticatedSessionCsrfRefreshService(
        sessions, protector, rotations, tokenGenerator, tokenHasher, clock);
  }

  private ProtectedAuthenticatedSession protectedSession() {
    return new ProtectedAuthenticatedSession(
        sid.value(), AuthenticatedSessionStatus.ACTIVE,
        new ProtectedAuthenticatedSession.Data(
            Map.of(
                ProtectedAuthenticatedSession.CIPHERTEXT, "ct",
                ProtectedAuthenticatedSession.ALGORITHM, "AES_GCM_V1",
                ProtectedAuthenticatedSession.NONCE, "nonce",
                ProtectedAuthenticatedSession.AUTHENTICATION_TAG, "tag",
                ProtectedAuthenticatedSession.KEY_VERSION, "kv1")),
        NOW.minusSeconds(300), NOW.minusSeconds(60), NOW.plusSeconds(1800), NOW.plusSeconds(3600));
  }

  private AuthenticatedSession usableSession() {
    return new AuthenticatedSession(
        sid, new ActorContext("actor-1", ActorUserRole.ROLE_USER_MBR), List.of(),
        AuthenticatedSessionStatus.ACTIVE, "csrfhash", NOW.minusSeconds(300),
        NOW.minusSeconds(60), NOW.plusSeconds(1800), NOW.plusSeconds(3600));
  }

  private void givenUnprotectedSessionUsable() {
    when(protector.unprotect(any())).thenReturn(usableSession());
  }

  // ----- session-id / load gate ----------------------------------------------

  @Test
  void blankRawSessionIdYieldsSessionInvalid() {
    StepVerifier.create(newService().refresh("", "req-1"))
        .assertNext(o -> assertThat(o.status()).isEqualTo(RefreshCsrfTokenStatus.SESSION_INVALID))
        .verifyComplete();
  }

  @Test
  void sessionMissingYieldsSessionMissing() {
    when(sessions.load(any())).thenReturn(Mono.just(Optional.empty()));
    StepVerifier.create(newService().refresh(sid.value(), "req-1"))
        .assertNext(o -> assertThat(o.status()).isEqualTo(RefreshCsrfTokenStatus.SESSION_MISSING))
        .verifyComplete();
  }

  @Test
  void loadErrorYieldsSystemFailure() {
    when(sessions.load(any())).thenReturn(Mono.error(new IllegalStateException("boom")));
    StepVerifier.create(newService().refresh(sid.value(), "req-1"))
        .assertNext(o -> assertThat(o.status()).isEqualTo(RefreshCsrfTokenStatus.SYSTEM_FAILURE))
        .verifyComplete();
  }

  // ----- unprotect / usability gate -------------------------------------------

  @Test
  void unprotectFailureYieldsSessionInvalid() {
    when(sessions.load(any())).thenReturn(Mono.just(Optional.of(protectedSession())));
    when(protector.unprotect(any())).thenThrow(new IllegalStateException("boom"));
    StepVerifier.create(newService().refresh(sid.value(), "req-1"))
        .assertNext(o -> assertThat(o.status()).isEqualTo(RefreshCsrfTokenStatus.SESSION_INVALID))
        .verifyComplete();
  }

  @Test
  void notUsableSessionYieldsSessionNotUsable() {
    when(sessions.load(any())).thenReturn(Mono.just(Optional.of(protectedSession())));
    AuthenticatedSession revoked =
        new AuthenticatedSession(
            sid, new ActorContext("actor-1", ActorUserRole.ROLE_USER_MBR), List.of(),
            AuthenticatedSessionStatus.REVOKED, "csrfhash", NOW.minusSeconds(300),
            NOW.minusSeconds(60), NOW.plusSeconds(1800), NOW.plusSeconds(3600));
    when(protector.unprotect(any())).thenReturn(revoked);
    StepVerifier.create(newService().refresh(sid.value(), "req-1"))
        .assertNext(o -> assertThat(o.status()).isEqualTo(RefreshCsrfTokenStatus.SESSION_NOT_USABLE))
        .verifyComplete();
  }

  @Test
  void idleExpiryAlreadyPassedYieldsSessionNotUsable() {
    when(sessions.load(any())).thenReturn(Mono.just(Optional.of(protectedSession())));
    AuthenticatedSession almostExpired =
        new AuthenticatedSession(
            sid, new ActorContext("actor-1", ActorUserRole.ROLE_USER_MBR), List.of(),
            AuthenticatedSessionStatus.ACTIVE, "csrfhash", NOW.minusSeconds(3600),
            NOW.minusSeconds(3540), NOW.minusSeconds(1), NOW.plusSeconds(3600));
    when(protector.unprotect(any())).thenReturn(almostExpired);
    StepVerifier.create(newService().refresh(sid.value(), "req-1"))
        .assertNext(o -> assertThat(o.status()).isEqualTo(RefreshCsrfTokenStatus.SESSION_NOT_USABLE))
        .verifyComplete();
  }

  // ----- rotation outcome routing ---------------------------------------------

  private void givenTokenGeneration() {
    when(tokenGenerator.generate()).thenReturn("raw-csrf-token-12345");
    when(tokenHasher.hash(any())).thenReturn("hashed-csrf-token");
    when(protector.protect(any())).thenReturn(protectedSession());
  }

  private void givenRotationOutcome(AuthenticatedSessionCsrfRotationStatus status) {
    when(sessions.load(any())).thenReturn(Mono.just(Optional.of(protectedSession())));
    givenUnprotectedSessionUsable();
    givenTokenGeneration();
    when(rotations.rotateCsrfHashIfCurrent(any()))
        .thenReturn(Mono.just(new AuthenticatedSessionCsrfRotationResult(status)));
  }

  @Test
  void rotatedYieldsSuccess() {
    givenRotationOutcome(AuthenticatedSessionCsrfRotationStatus.ROTATED);
    StepVerifier.create(newService().refresh(sid.value(), "req-1"))
        .assertNext(
            o -> {
              assertThat(o.status()).isEqualTo(RefreshCsrfTokenStatus.SUCCESS);
              assertThat(o.csrfToken()).isEqualTo("raw-csrf-token-12345");
            })
        .verifyComplete();
  }

  @Test
  void conflictYieldsConflict() {
    givenRotationOutcome(AuthenticatedSessionCsrfRotationStatus.CONFLICT);
    StepVerifier.create(newService().refresh(sid.value(), "req-1"))
        .assertNext(o -> assertThat(o.status()).isEqualTo(RefreshCsrfTokenStatus.CONFLICT))
        .verifyComplete();
  }

  @Test
  void notFoundYieldsSessionMissing() {
    givenRotationOutcome(AuthenticatedSessionCsrfRotationStatus.NOT_FOUND);
    StepVerifier.create(newService().refresh(sid.value(), "req-1"))
        .assertNext(o -> assertThat(o.status()).isEqualTo(RefreshCsrfTokenStatus.SESSION_MISSING))
        .verifyComplete();
  }

  @Test
  void expiredYieldsSessionNotUsable() {
    givenRotationOutcome(AuthenticatedSessionCsrfRotationStatus.EXPIRED);
    StepVerifier.create(newService().refresh(sid.value(), "req-1"))
        .assertNext(o -> assertThat(o.status()).isEqualTo(RefreshCsrfTokenStatus.SESSION_NOT_USABLE))
        .verifyComplete();
  }

  @Test
  void rotationSystemFailureYieldsSystemFailure() {
    givenRotationOutcome(AuthenticatedSessionCsrfRotationStatus.SYSTEM_FAILURE);
    StepVerifier.create(newService().refresh(sid.value(), "req-1"))
        .assertNext(o -> assertThat(o.status()).isEqualTo(RefreshCsrfTokenStatus.SYSTEM_FAILURE))
        .verifyComplete();
  }

  @Test
  void rotationErrorYieldsSystemFailure() {
    when(sessions.load(any())).thenReturn(Mono.just(Optional.of(protectedSession())));
    givenUnprotectedSessionUsable();
    givenTokenGeneration();
    when(rotations.rotateCsrfHashIfCurrent(any())).thenReturn(Mono.error(new IllegalStateException("boom")));
    StepVerifier.create(newService().refresh(sid.value(), "req-1"))
        .assertNext(o -> assertThat(o.status()).isEqualTo(RefreshCsrfTokenStatus.SYSTEM_FAILURE))
        .verifyComplete();
  }
}
