package com.bct.ngtpa.apiservice.application.usecase.session.authenticated;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.ActorContext;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSession;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionCasUpdateCommand;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionCasUpdateOutcome;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionCasUpdateResult;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionPersistenceResult;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionRevisionGenerator;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionStatus;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.DeleteAuthenticatedSessionCommand;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.LoadAuthenticatedSessionCommand;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.ProtectedAuthenticatedSession;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.RevokeAuthenticatedSessionCommand;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.SessionRevision;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.StoreAuthenticatedSessionCommand;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.TouchAuthenticatedSessionCommand;
import com.bct.ngtpa.apiservice.application.port.out.AuthenticatedSessionCasUpdatePort;
import com.bct.ngtpa.apiservice.application.port.out.AuthenticatedSessionPort;
import com.bct.ngtpa.apiservice.application.port.out.AuthenticatedSessionProtectorPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Fail-first tests for the new generic reload-per-attempt CAS retry wrapper around {@link
 * AuthenticatedSessionCasUpdatePort}.
 *
 * <p>Mechanism confirmed in scripts 065/066 by tracing {@code AuthFlowSessionConcurrencyService}
 * -&gt; {@code RevisionAwareAuthFlowSessionWriter}: the ENTIRE load-mutate-write sequence is
 * wrapped in {@code Mono.defer(...)} so each retry attempt re-loads fresh state (a fresh
 * revision), rather than blindly retrying the same stale command.
 *
 * <p>Two deliberate departures from that precedent (see script 067's header comment for full
 * rationale): only {@code REVISION_MISMATCH} is retried (not every non-APPLIED outcome), and the
 * service returns a typed {@code AuthenticatedSessionConcurrencyResult} rather than throwing.
 */
class AuthenticatedSessionConcurrencyServiceTest {

  private static final Instant NOW = Instant.parse("2026-09-24T00:00:00Z");
  private static final AuthenticatedSessionId ID =
      new AuthenticatedSessionId("sid_abcdefghijklmnopqrstuvwx");

  private final FakeSessionPort sessionPort = new FakeSessionPort();
  private final FakeProtector protector = new FakeProtector();
  private final FakeCasUpdatePort casPort = new FakeCasUpdatePort();
  private final FakeRevisionGenerator revisions = new FakeRevisionGenerator();
  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

  private AuthenticatedSessionConcurrencyService newService() {
    return new AuthenticatedSessionConcurrencyService(
        sessionPort, protector, casPort, revisions, clock);
  }

  private AuthenticatedSession usableSession(SessionRevision revision, String csrfMarker) {
    return new AuthenticatedSession(
        ID,
        new ActorContext("member-1", ActorUserRole.ROLE_USER_MBR),
        List.of(),
        AuthenticatedSessionStatus.ACTIVE,
        csrfMarker,
        NOW.minusSeconds(60),
        NOW.minusSeconds(60),
        NOW.plusSeconds(900),
        NOW.plusSeconds(28800),
        revision);
  }

  private static UnaryOperator<AuthenticatedSession> markingMutator(List<String> observedInputs) {
    return current -> {
      observedInputs.add(current.csrfTokenHash());
      return new AuthenticatedSession(
          current.authenticatedSessionId(),
          current.actorContext(),
          current.accounts(),
          current.status(),
          "mutated-" + current.csrfTokenHash(),
          current.createdAt(),
          current.lastAccessedAt(),
          current.idleExpiresAt(),
          current.absoluteExpiresAt(),
          current.revision());
    };
  }

  // ----- success path -----------------------------------------------------

  @Test
  void successfulUpdateOnFirstAttemptAppliesMutatorAndStampsAFreshGeneratedRevision() {
    var expected = new SessionRevision("a".repeat(32));
    var fresh = new SessionRevision("b".repeat(32));
    sessionPort.enqueueLoad(protector.protect(usableSession(expected, "original")));
    revisions.enqueue(fresh);
    casPort.enqueue(AuthenticatedSessionCasUpdateOutcome.APPLIED);

    List<String> observed = new ArrayList<>();
    var result =
        newService().updateIfUsable(ID, markingMutator(observed), "req-1").block();

    assertThat(result.outcome()).isEqualTo(AuthenticatedSessionConcurrencyOutcome.APPLIED);
    assertThat(result.session().csrfTokenHash()).isEqualTo("mutated-original");
    assertThat(observed).containsExactly("original");
    assertThat(sessionPort.loadCount).isEqualTo(1);
    assertThat(casPort.receivedCommands).hasSize(1);
    AuthenticatedSessionCasUpdateCommand sent = casPort.receivedCommands.get(0);
    assertThat(sent.expectedRevision()).isEqualTo(expected);
    assertThat(sent.replacementRevision()).isEqualTo(fresh);
    assertThat(sent.correlationId()).isEqualTo("req-1");
  }

  // ----- terminal, non-retried outcomes -------------------------------------

  @Test
  void sessionNotFoundIsTerminalAndNeverCallsCasPort() {
    // no queued load -> Optional.empty()
    var result = newService().updateIfUsable(ID, UnaryOperator.identity(), "req-1").block();

    assertThat(result.outcome()).isEqualTo(AuthenticatedSessionConcurrencyOutcome.SESSION_NOT_FOUND);
    assertThat(result.session()).isNull();
    assertThat(casPort.receivedCommands).isEmpty();
  }

  @Test
  void expiredSessionIsTerminalAndNeverCallsCasPort() {
    var revision = new SessionRevision("c".repeat(32));
    var expired =
        new AuthenticatedSession(
            ID,
            new ActorContext("member-1", ActorUserRole.ROLE_USER_MBR),
            List.of(),
            AuthenticatedSessionStatus.ACTIVE,
            "hash",
            NOW.minusSeconds(3600),
            NOW.minusSeconds(3600),
            NOW.minusSeconds(1), // idle-expired
            NOW.plusSeconds(28800),
            revision);
    sessionPort.enqueueLoad(protector.protect(expired));

    var result = newService().updateIfUsable(ID, UnaryOperator.identity(), "req-1").block();

    assertThat(result.outcome()).isEqualTo(AuthenticatedSessionConcurrencyOutcome.SESSION_NOT_USABLE);
    assertThat(casPort.receivedCommands).isEmpty();
  }

  @Test
  void casPortNotFoundIsTerminalAndDoesNotRetry() {
    var revision = new SessionRevision("d".repeat(32));
    sessionPort.enqueueLoad(protector.protect(usableSession(revision, "original")));
    revisions.enqueue(new SessionRevision("e".repeat(32)));
    casPort.enqueue(AuthenticatedSessionCasUpdateOutcome.NOT_FOUND);

    var result =
        newService().updateIfUsable(ID, UnaryOperator.identity(), "req-1").block();

    assertThat(result.outcome()).isEqualTo(AuthenticatedSessionConcurrencyOutcome.SYSTEM_FAILURE);
    assertThat(sessionPort.loadCount).isEqualTo(1);
    assertThat(casPort.receivedCommands).hasSize(1);
  }

  @Test
  void casPortCorruptStateMapsToSystemFailureAndDoesNotRetry() {
    var revision = new SessionRevision("f".repeat(32));
    sessionPort.enqueueLoad(protector.protect(usableSession(revision, "original")));
    revisions.enqueue(new SessionRevision("0".repeat(32)));
    casPort.enqueue(AuthenticatedSessionCasUpdateOutcome.CORRUPT_STATE);

    var result =
        newService().updateIfUsable(ID, UnaryOperator.identity(), "req-1").block();

    assertThat(result.outcome()).isEqualTo(AuthenticatedSessionConcurrencyOutcome.SYSTEM_FAILURE);
    assertThat(sessionPort.loadCount).isEqualTo(1);
  }

  // ----- retryable conflict: reload-per-attempt semantics --------------------

  @Test
  void revisionMismatchRetriesWithFreshReloadAndSucceedsOnSecondAttempt() {
    var firstRevision = new SessionRevision("1".repeat(32));
    var secondRevision = new SessionRevision("2".repeat(32));
    sessionPort.enqueueLoad(protector.protect(usableSession(firstRevision, "attempt-1")));
    sessionPort.enqueueLoad(protector.protect(usableSession(secondRevision, "attempt-2")));
    revisions.enqueue(new SessionRevision("3".repeat(32)));
    revisions.enqueue(new SessionRevision("4".repeat(32)));
    casPort.enqueue(AuthenticatedSessionCasUpdateOutcome.REVISION_MISMATCH);
    casPort.enqueue(AuthenticatedSessionCasUpdateOutcome.APPLIED);

    List<String> observed = new ArrayList<>();
    var result =
        newService().updateIfUsable(ID, markingMutator(observed), "req-1").block();

    assertThat(result.outcome()).isEqualTo(AuthenticatedSessionConcurrencyOutcome.APPLIED);
    // Proves the mutator saw the FRESHLY reloaded state on retry, not a stale closure.
    assertThat(observed).containsExactly("attempt-1", "attempt-2");
    assertThat(sessionPort.loadCount).isEqualTo(2);
    assertThat(casPort.receivedCommands).hasSize(2);
    assertThat(casPort.receivedCommands.get(0).expectedRevision()).isEqualTo(firstRevision);
    assertThat(casPort.receivedCommands.get(1).expectedRevision()).isEqualTo(secondRevision);
  }

  @Test
  void revisionMismatchExhaustsAllRetriesAndReturnsConflictExhausted() {
    for (int i = 0; i < 3; i++) {
      sessionPort.enqueueLoad(
          protector.protect(usableSession(new SessionRevision(String.valueOf(i).repeat(32)), "x")));
      revisions.enqueue(new SessionRevision(("9" + i).repeat(16)));
      casPort.enqueue(AuthenticatedSessionCasUpdateOutcome.REVISION_MISMATCH);
    }

    var result =
        newService().updateIfUsable(ID, UnaryOperator.identity(), "req-1").block();

    assertThat(result.outcome())
        .isEqualTo(AuthenticatedSessionConcurrencyOutcome.REVISION_CONFLICT_EXHAUSTED);
    assertThat(result.session()).isNull();
    // Retry.backoff(2, ...) => 1 initial + 2 retries = 3 total attempts.
    assertThat(sessionPort.loadCount).isEqualTo(3);
    assertThat(casPort.receivedCommands).hasSize(3);
  }

  // ----- correlation id passthrough -----------------------------------------

  @Test
  void correlationIdIsNeverPassedAsAnyRevisionOrKeyValue() {
    var revision = new SessionRevision("5".repeat(32));
    sessionPort.enqueueLoad(protector.protect(usableSession(revision, "original")));
    revisions.enqueue(new SessionRevision("6".repeat(32)));
    casPort.enqueue(AuthenticatedSessionCasUpdateOutcome.APPLIED);

    newService().updateIfUsable(ID, UnaryOperator.identity(), "secret-correlation").block();

    AuthenticatedSessionCasUpdateCommand sent = casPort.receivedCommands.get(0);
    assertThat(sent.expectedRevision().value()).doesNotContain("secret-correlation");
    assertThat(sent.replacementRevision().value()).doesNotContain("secret-correlation");
    assertThat(sent.correlationId()).isEqualTo("secret-correlation");
  }

  // ----- fakes ---------------------------------------------------------------

  private static final class FakeSessionPort implements AuthenticatedSessionPort {
    private final Deque<ProtectedAuthenticatedSession> queuedLoads = new ArrayDeque<>();
    int loadCount;

    void enqueueLoad(ProtectedAuthenticatedSession envelope) {
      queuedLoads.add(envelope);
    }

    @Override
    public Mono<AuthenticatedSessionPersistenceResult> store(StoreAuthenticatedSessionCommand c) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Mono<Optional<ProtectedAuthenticatedSession>> load(LoadAuthenticatedSessionCommand c) {
      loadCount++;
      return Mono.just(Optional.ofNullable(queuedLoads.poll()));
    }

    @Override
    public Mono<AuthenticatedSessionPersistenceResult> touch(TouchAuthenticatedSessionCommand c) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Mono<AuthenticatedSessionPersistenceResult> revoke(RevokeAuthenticatedSessionCommand c) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Mono<AuthenticatedSessionPersistenceResult> delete(DeleteAuthenticatedSessionCommand c) {
      throw new UnsupportedOperationException();
    }
  }

  private static final class FakeProtector implements AuthenticatedSessionProtectorPort {
    private final Map<String, AuthenticatedSession> registry = new HashMap<>();
    private int sequence;

    @Override
    public ProtectedAuthenticatedSession protect(AuthenticatedSession session) {
      String token = "tok-" + (++sequence);
      registry.put(token, session);
      var data =
          new ProtectedAuthenticatedSession.Data(
              Map.of(
                  ProtectedAuthenticatedSession.CIPHERTEXT, token,
                  ProtectedAuthenticatedSession.ALGORITHM, "AES/GCM/NoPadding",
                  ProtectedAuthenticatedSession.NONCE, "n",
                  ProtectedAuthenticatedSession.AUTHENTICATION_TAG, "t",
                  ProtectedAuthenticatedSession.KEY_VERSION, "v1"));
      return new ProtectedAuthenticatedSession(
          session.authenticatedSessionId().value(),
          session.status(),
          data,
          session.createdAt(),
          session.lastAccessedAt(),
          session.idleExpiresAt(),
          session.absoluteExpiresAt(),
          session.revision());
    }

    @Override
    public AuthenticatedSession unprotect(ProtectedAuthenticatedSession envelope) {
      AuthenticatedSession found = registry.get(envelope.ciphertext());
      if (found == null) {
        throw new IllegalStateException("test fixture error: unknown ciphertext token");
      }
      return found;
    }
  }

  private static final class FakeCasUpdatePort implements AuthenticatedSessionCasUpdatePort {
    private final Deque<AuthenticatedSessionCasUpdateOutcome> queuedOutcomes = new ArrayDeque<>();
    private final List<AuthenticatedSessionCasUpdateCommand> receivedCommands = new ArrayList<>();

    void enqueue(AuthenticatedSessionCasUpdateOutcome outcome) {
      queuedOutcomes.add(outcome);
    }

    @Override
    public Mono<AuthenticatedSessionCasUpdateResult> updateIfCurrent(
        AuthenticatedSessionCasUpdateCommand command) {
      receivedCommands.add(command);
      AuthenticatedSessionCasUpdateOutcome outcome =
          queuedOutcomes.isEmpty()
              ? AuthenticatedSessionCasUpdateOutcome.SYSTEM_FAILURE
              : queuedOutcomes.poll();
      return Mono.just(AuthenticatedSessionCasUpdateResult.of(outcome));
    }
  }

  private static final class FakeRevisionGenerator implements AuthenticatedSessionRevisionGenerator {
    private final Deque<SessionRevision> queued = new ArrayDeque<>();

    void enqueue(SessionRevision revision) {
      queued.add(revision);
    }

    @Override
    public SessionRevision generate() {
      return queued.isEmpty() ? new SessionRevision("f".repeat(32)) : queued.poll();
    }
  }
}
