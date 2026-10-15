package com.bct.ngtpa.apiservice.application.usecase.auth.csrf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.application.dto.ActorContext;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.auth.csrf.RefreshCsrfTokenStatus;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSession;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionCasUpdateCommand;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionCasUpdateOutcome;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionCasUpdateResult;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionStatus;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.ProtectedAuthenticatedSession;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.SessionRevision;
import com.bct.ngtpa.apiservice.application.port.out.AuthenticatedSessionCasUpdatePort;
import com.bct.ngtpa.apiservice.application.port.out.AuthenticatedSessionPort;
import com.bct.ngtpa.apiservice.application.port.out.AuthenticatedSessionProtectorPort;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.AuthenticatedSessionConcurrencyService;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.CsrfTokenGenerator;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.CsrfTokenHasher;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Branch-oriented reactive tests for {@link AuthenticatedSessionCsrfRefreshService}, with the real
 * {@link AuthenticatedSessionConcurrencyService} over a queued compare-and-set port.
 */
class AuthenticatedSessionCsrfRefreshServiceTest {

  private static final Instant NOW = Instant.parse("2026-09-14T00:00:00Z");
  private static final SessionRevision STORED = new SessionRevision("0".repeat(32));
  private static final SessionRevision FRESH = new SessionRevision("1".repeat(32));
  private static final String RAW_TOKEN = "raw-csrf-token-12345";
  private static final String HASHED_TOKEN = "hashed-csrf-token";

  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
  private final AuthenticatedSessionPort sessions = mock(AuthenticatedSessionPort.class);
  private final AuthenticatedSessionProtectorPort protector =
      mock(AuthenticatedSessionProtectorPort.class);
  private final QueuedCasPort casPort = new QueuedCasPort();
  private final CsrfTokenGenerator tokenGenerator = mock(CsrfTokenGenerator.class);
  private final CsrfTokenHasher tokenHasher = mock(CsrfTokenHasher.class);

  private final AuthenticatedSessionId sid =
      new AuthenticatedSessionId("sid_abcdefghijklmnopqrstuvwx");

  private AuthenticatedSessionCsrfRefreshService newService() {
    var concurrency =
        new AuthenticatedSessionConcurrencyService(
            sessions, protector, casPort, () -> FRESH, clock);
    return new AuthenticatedSessionCsrfRefreshService(
        sessions, protector, concurrency, tokenGenerator, tokenHasher, clock);
  }

  private ProtectedAuthenticatedSession protectedSession() {
    return new ProtectedAuthenticatedSession(
        sid.value(),
        AuthenticatedSessionStatus.ACTIVE,
        new ProtectedAuthenticatedSession.Data(
            Map.of(
                ProtectedAuthenticatedSession.CIPHERTEXT, "ct",
                ProtectedAuthenticatedSession.ALGORITHM, "AES_GCM_V1",
                ProtectedAuthenticatedSession.NONCE, "nonce",
                ProtectedAuthenticatedSession.AUTHENTICATION_TAG, "tag",
                ProtectedAuthenticatedSession.KEY_VERSION, "kv1")),
        NOW.minusSeconds(300),
        NOW.minusSeconds(60),
        NOW.plusSeconds(1800),
        NOW.plusSeconds(3600),
        STORED);
  }

  private AuthenticatedSession session(
      AuthenticatedSessionStatus status,
      Instant createdAt,
      Instant lastAccessedAt,
      Instant idleExpiresAt) {
    return new AuthenticatedSession(
        sid,
        new ActorContext("actor-1", ActorUserRole.ROLE_USER_MBR),
        List.of(),
        status,
        "csrfhash",
        createdAt,
        lastAccessedAt,
        idleExpiresAt,
        NOW.plusSeconds(3600),
        STORED,
        null);
  }

  private AuthenticatedSession usableSession() {
    return session(
        AuthenticatedSessionStatus.ACTIVE,
        NOW.minusSeconds(300),
        NOW.minusSeconds(60),
        NOW.plusSeconds(1800));
  }

  private AuthenticatedSession revokedSession() {
    return session(
        AuthenticatedSessionStatus.REVOKED,
        NOW.minusSeconds(300),
        NOW.minusSeconds(60),
        NOW.plusSeconds(1800));
  }

  private void givenStoredSession() {
    when(sessions.load(any())).thenReturn(Mono.just(Optional.of(protectedSession())));
  }

  private void givenTokenGeneration() {
    when(tokenGenerator.generate()).thenReturn(RAW_TOKEN);
    when(tokenHasher.hash(any())).thenReturn(HASHED_TOKEN);
    when(protector.protect(any())).thenReturn(protectedSession());
  }

  private void givenRotatableSession() {
    givenStoredSession();
    when(protector.unprotect(any())).thenReturn(usableSession());
    givenTokenGeneration();
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
    givenStoredSession();
    when(protector.unprotect(any())).thenThrow(new IllegalStateException("boom"));
    StepVerifier.create(newService().refresh(sid.value(), "req-1"))
        .assertNext(o -> assertThat(o.status()).isEqualTo(RefreshCsrfTokenStatus.SESSION_INVALID))
        .verifyComplete();
    assertThat(casPort.received).isEmpty();
  }

  @Test
  void notUsableSessionYieldsSessionNotUsable() {
    givenStoredSession();
    when(protector.unprotect(any())).thenReturn(revokedSession());
    StepVerifier.create(newService().refresh(sid.value(), "req-1"))
        .assertNext(
            o -> assertThat(o.status()).isEqualTo(RefreshCsrfTokenStatus.SESSION_NOT_USABLE))
        .verifyComplete();
    assertThat(casPort.received).isEmpty();
  }

  @Test
  void idleExpiryAlreadyPassedYieldsSessionNotUsable() {
    givenStoredSession();
    when(protector.unprotect(any()))
        .thenReturn(
            session(
                AuthenticatedSessionStatus.ACTIVE,
                NOW.minusSeconds(3600),
                NOW.minusSeconds(3540),
                NOW.minusSeconds(1)));
    StepVerifier.create(newService().refresh(sid.value(), "req-1"))
        .assertNext(
            o -> assertThat(o.status()).isEqualTo(RefreshCsrfTokenStatus.SESSION_NOT_USABLE))
        .verifyComplete();
    assertThat(casPort.received).isEmpty();
  }

  // ----- revision compare-and-set outcomes --------------------------------------

  @Test
  void appliedWriteRotatesOnlyTheCsrfHashAndReturnsTheRawToken() {
    givenRotatableSession();
    casPort.queue(AuthenticatedSessionCasUpdateOutcome.APPLIED);

    StepVerifier.create(newService().refresh(sid.value(), "req-1"))
        .assertNext(
            o -> {
              assertThat(o.status()).isEqualTo(RefreshCsrfTokenStatus.SUCCESS);
              assertThat(o.csrfToken()).isEqualTo(RAW_TOKEN);
            })
        .verifyComplete();

    ArgumentCaptor<AuthenticatedSession> written =
        ArgumentCaptor.forClass(AuthenticatedSession.class);
    verify(protector).protect(written.capture());
    AuthenticatedSession original = usableSession();
    AuthenticatedSession replacement = written.getValue();
    assertThat(replacement.csrfTokenHash()).isEqualTo(HASHED_TOKEN);
    assertThat(replacement.revision()).isEqualTo(FRESH);
    assertThat(replacement.actorContext()).isEqualTo(original.actorContext());
    assertThat(replacement.status()).isEqualTo(original.status());
    assertThat(replacement.createdAt()).isEqualTo(original.createdAt());
    assertThat(replacement.lastAccessedAt()).isEqualTo(original.lastAccessedAt());
    assertThat(replacement.idleExpiresAt()).isEqualTo(original.idleExpiresAt());
    assertThat(replacement.absoluteExpiresAt()).isEqualTo(original.absoluteExpiresAt());
    assertThat(casPort.received)
        .singleElement()
        .satisfies(command -> assertThat(command.expectedRevision()).isEqualTo(STORED));
  }

  @Test
  void exhaustedRevisionConflictYieldsConflictWithoutToken() {
    givenRotatableSession();
    casPort.fallback = AuthenticatedSessionCasUpdateOutcome.REVISION_MISMATCH;

    StepVerifier.create(newService().refresh(sid.value(), "req-1"))
        .assertNext(
            o -> {
              assertThat(o.status()).isEqualTo(RefreshCsrfTokenStatus.CONFLICT);
              assertThat(o.csrfToken()).isNull();
            })
        .verifyComplete();
    assertThat(casPort.received).hasSize(3);
  }

  @Test
  void sessionRemovedBeforeTheWriteYieldsSessionMissing() {
    when(sessions.load(any()))
        .thenReturn(
            Mono.just(Optional.of(protectedSession())),
            Mono.just(Optional.<ProtectedAuthenticatedSession>empty()));
    when(protector.unprotect(any())).thenReturn(usableSession());
    givenTokenGeneration();

    StepVerifier.create(newService().refresh(sid.value(), "req-1"))
        .assertNext(o -> assertThat(o.status()).isEqualTo(RefreshCsrfTokenStatus.SESSION_MISSING))
        .verifyComplete();
    assertThat(casPort.received).isEmpty();
  }

  @Test
  void sessionUnusableAtTheWriteYieldsSessionNotUsable() {
    givenStoredSession();
    when(protector.unprotect(any())).thenReturn(usableSession(), revokedSession());
    givenTokenGeneration();

    StepVerifier.create(newService().refresh(sid.value(), "req-1"))
        .assertNext(
            o -> assertThat(o.status()).isEqualTo(RefreshCsrfTokenStatus.SESSION_NOT_USABLE))
        .verifyComplete();
    assertThat(casPort.received).isEmpty();
  }

  @Test
  void nonRetryableWriteOutcomeYieldsSystemFailure() {
    givenRotatableSession();
    casPort.queue(AuthenticatedSessionCasUpdateOutcome.CORRUPT_STATE);

    StepVerifier.create(newService().refresh(sid.value(), "req-1"))
        .assertNext(o -> assertThat(o.status()).isEqualTo(RefreshCsrfTokenStatus.SYSTEM_FAILURE))
        .verifyComplete();
  }

  @Test
  void writeErrorYieldsSystemFailure() {
    givenRotatableSession();
    casPort.queueError(new IllegalStateException("boom"));

    StepVerifier.create(newService().refresh(sid.value(), "req-1"))
        .assertNext(o -> assertThat(o.status()).isEqualTo(RefreshCsrfTokenStatus.SYSTEM_FAILURE))
        .verifyComplete();
  }

  // ----- fakes -----------------------------------------------------------------

  private static final class QueuedCasPort implements AuthenticatedSessionCasUpdatePort {
    private final Deque<Mono<AuthenticatedSessionCasUpdateResult>> queued = new ArrayDeque<>();
    private final List<AuthenticatedSessionCasUpdateCommand> received = new ArrayList<>();
    private AuthenticatedSessionCasUpdateOutcome fallback =
        AuthenticatedSessionCasUpdateOutcome.SYSTEM_FAILURE;

    void queue(AuthenticatedSessionCasUpdateOutcome outcome) {
      queued.add(Mono.just(AuthenticatedSessionCasUpdateResult.of(outcome)));
    }

    void queueError(RuntimeException error) {
      queued.add(Mono.error(error));
    }

    @Override
    public Mono<AuthenticatedSessionCasUpdateResult> updateIfCurrent(
        AuthenticatedSessionCasUpdateCommand command) {
      received.add(command);
      Mono<AuthenticatedSessionCasUpdateResult> next = queued.poll();
      return next != null ? next : Mono.just(AuthenticatedSessionCasUpdateResult.of(fallback));
    }
  }
}
