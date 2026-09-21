package com.bct.ngtpa.apiservice.adapter.out.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationEligibleTrustSet;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationLookupAccount;
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
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/** Branch-oriented reactive tests for {@link RegistrationEligibleTrustSessionAdapter}. */
class RegistrationEligibleTrustSessionAdapterTest {

  private static final Instant NOW = Instant.parse("2026-09-14T00:00:00Z");
  private final AuthFlowSessionPort sessions = mock(AuthFlowSessionPort.class);
  private final AuthFlowSessionId sid = new AuthFlowSessionId("afs-reg-trust-1");

  private RegistrationEligibleTrustSessionAdapter newAdapter() {
    return new RegistrationEligibleTrustSessionAdapter(sessions);
  }

  private static RegistrationLookupAccount account() {
    return new RegistrationLookupAccount(
        "env1", "trust1", "SCHEME_A", "policy1", "cert1", "ACTIVE", "2026-01-01", true, "cust1");
  }

  private AuthFlowSession session() {
    return new AuthFlowSession(
        sid, AuthFlowScenario.REGISTRATION, AuthFlowStatus.STARTED, "csrfhash", null,
        new AuthFlowSessionData("email", "j***@example.com", Map.of()),
        NOW.minusSeconds(120), NOW.minusSeconds(60), NOW.plusSeconds(1800));
  }

  private AuthFlowSessionSnapshot snapshotOf(AuthFlowSession session) {
    return new AuthFlowSessionSnapshot(session, new SessionRevision("0".repeat(32)));
  }

  // ----- guards ---------------------------------------------------------------

  @Test
  void nullSessionIdThrows() {
    assertThatThrownBy(() -> newAdapter().store(null, List.of(account()), NOW))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void nullNowThrows() {
    assertThatThrownBy(() -> newAdapter().store(sid, List.of(account()), null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void emptyAccountsListThrows() {
    assertThatThrownBy(() -> newAdapter().store(sid, List.of(), NOW))
        .isInstanceOf(IllegalArgumentException.class);
  }

  // ----- session missing -----------------------------------------------------------

  @Test
  void sessionMissingErrors() {
    when(sessions.load(any())).thenReturn(Mono.just(Optional.empty()));
    StepVerifier.create(newAdapter().store(sid, List.of(account()), NOW))
        .expectError(IllegalStateException.class)
        .verify();
  }

  // ----- success: with existing data / with null data -------------------------------

  @Test
  void storeSucceedsWithExistingSessionData() {
    AuthFlowSession currentSession = session();
    when(sessions.load(any())).thenReturn(Mono.just(Optional.of(currentSession)));
    // NOTE (round 139): RevisionAwareAuthFlowSessionWriter.updateExpected(...)
    // (used internally by the adapter, confirmed round 137) calls
    // sessions.loadSnapshot(id) - a DIFFERENT method than sessions.load(...)
    // - and compares snapshot.session() against the SAME "current" session
    // object for equality. Both stubs must return data consistent with the
    // SAME session instance.
    when(sessions.loadSnapshot(any())).thenReturn(Mono.just(Optional.of(snapshotOf(currentSession))));
    when(sessions.updateRevisioned(any()))
        .thenReturn(
            Mono.just(
                new AuthFlowSessionMutationResult(
                    AuthFlowSessionMutationOutcome.APPLIED, snapshotOf(currentSession))));
    StepVerifier.create(newAdapter().store(sid, List.of(account()), NOW)).verifyComplete();
  }

  @Test
  void storeSucceedsWithNullSessionData() {
    AuthFlowSession sessionWithNullData =
        new AuthFlowSession(
            sid, AuthFlowScenario.REGISTRATION, AuthFlowStatus.STARTED, "csrfhash", null, null,
            NOW.minusSeconds(120), NOW.minusSeconds(60), NOW.plusSeconds(1800));
    when(sessions.load(any())).thenReturn(Mono.just(Optional.of(sessionWithNullData)));
    when(sessions.loadSnapshot(any()))
        .thenReturn(Mono.just(Optional.of(snapshotOf(sessionWithNullData))));
    when(sessions.updateRevisioned(any()))
        .thenReturn(
            Mono.just(
                new AuthFlowSessionMutationResult(
                    AuthFlowSessionMutationOutcome.APPLIED, snapshotOf(session()))));
    StepVerifier.create(newAdapter().store(sid, List.of(account()), NOW)).verifyComplete();
  }

  // ----- revision conflict from the underlying writer -----------------------------

  @Test
  void storeRevisionConflictErrors() {
    AuthFlowSession currentSession = session();
    when(sessions.load(any())).thenReturn(Mono.just(Optional.of(currentSession)));
    when(sessions.loadSnapshot(any())).thenReturn(Mono.just(Optional.of(snapshotOf(currentSession))));
    when(sessions.updateRevisioned(any()))
        .thenReturn(
            Mono.just(
                new AuthFlowSessionMutationResult(
                    AuthFlowSessionMutationOutcome.SESSION_REVISION_CHANGED,
                    snapshotOf(currentSession))));
    StepVerifier.create(newAdapter().store(sid, List.of(account()), NOW))
        .expectError(IllegalStateException.class)
        .verify();
  }
}
