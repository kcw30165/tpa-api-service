package com.bct.ngtpa.apiservice.adapter.out.redis.auth.usernamerecovery.replay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryStartResult;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.context.UsernameRecoveryAuthFlowContext;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowScenario;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionData;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionSnapshot;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowStatus;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.SessionRevision;
import com.bct.ngtpa.apiservice.application.idempotency.ProtectedReplayReference;
import com.bct.ngtpa.apiservice.application.port.in.CreateAuthFlowSessionCsrfUseCase;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort;
import com.bct.ngtpa.apiservice.application.service.auth.usernamerecovery.context.UsernameRecoveryAuthFlowContextBinding;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency.start.ProtectedUsernameRecoveryStartReplay;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency.start.UsernameRecoveryStartProtectedReplayProtectorPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency.start.UsernameRecoveryStartProtectedReplayReferenceGenerator;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency.start.UsernameRecoveryStartProtectedReplayState;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.CreateAuthFlowSessionCsrfResult;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySchemeRef;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySessionCandidate;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryWorkflowStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Branch-oriented reactive tests for {@link RedisUsernameRecoveryStartProtectedReplayAdapter}.
 * All 8 collaborators mocked directly, mirroring the proven Redis pattern (rounds 28-33, 41-43).
 * {@code UsernameRecoveryAuthFlowContext} is MOCKED rather than constructed for real: its compact
 * constructor has heavy cross-field validation on candidate lists that is irrelevant to this
 * adapter's own branch coverage, since {@code contexts} is itself a mocked collaborator. The real
 * {@code authorizeAndHydrate} source (confirmed verbatim) maps EVERY candidate directly into a
 * {@code UsernameRecoverySchemeProjection} with no filtering, so the happy path uses one mocked
 * candidate with all 4 accessors stubbed.
 */
@SuppressWarnings("unchecked")
class RedisUsernameRecoveryStartProtectedReplayAdapterTest {

  private static final Instant NOW = Instant.parse("2026-09-14T00:00:00Z");
  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

  private final ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
  private final ReactiveValueOperations<String, String> values = mock(ReactiveValueOperations.class);
  private final RedisUsernameRecoveryStartProtectedReplayKeyFactory keys =
      mock(RedisUsernameRecoveryStartProtectedReplayKeyFactory.class);
  private final RedisUsernameRecoveryStartProtectedReplayJsonMapper json =
      mock(RedisUsernameRecoveryStartProtectedReplayJsonMapper.class);
  private final UsernameRecoveryStartProtectedReplayProtectorPort protector =
      mock(UsernameRecoveryStartProtectedReplayProtectorPort.class);
  private final UsernameRecoveryStartProtectedReplayReferenceGenerator references =
      mock(UsernameRecoveryStartProtectedReplayReferenceGenerator.class);
  private final AuthFlowSessionPort sessions = mock(AuthFlowSessionPort.class);
  private final UsernameRecoveryAuthFlowContextBinding contexts =
      mock(UsernameRecoveryAuthFlowContextBinding.class);
  private final CreateAuthFlowSessionCsrfUseCase csrf = mock(CreateAuthFlowSessionCsrfUseCase.class);

  private final AuthFlowSessionId sid = new AuthFlowSessionId("afs-unamerec-1");
  private final ProtectedReplayReference reference =
      new ProtectedReplayReference("replay-ref-1234567890abcd");

  private RedisUsernameRecoveryStartProtectedReplayAdapter newAdapter() {
    return new RedisUsernameRecoveryStartProtectedReplayAdapter(
        redis, keys, json, protector, references, sessions, contexts, csrf, clock);
  }

  private void givenRedisReady() {
    when(redis.opsForValue()).thenReturn(values);
    when(keys.keyFor(any())).thenReturn("redis-key-1");
  }

  private UsernameRecoveryStartProtectedReplayState state(Instant expiresAt) {
    return new UsernameRecoveryStartProtectedReplayState(sid, expiresAt);
  }

  private AuthFlowSession sessionWith(
      AuthFlowScenario scenario, AuthFlowStatus status, Instant expiresAt) {
    return new AuthFlowSession(
        sid, scenario, status, "csrfhash", null,
        new AuthFlowSessionData("email", "j***@example.com", Map.of()),
        NOW.minusSeconds(120), NOW.minusSeconds(60), expiresAt);
  }

  private AuthFlowSessionSnapshot snapshotOf(AuthFlowSession session) {
    return new AuthFlowSessionSnapshot(session, new SessionRevision("0".repeat(32)));
  }

  // ----- store() ---------------------------------------------------------------

  @Test
  void storeWithNullTtlErrors() {
    StepVerifier.create(newAdapter().store(state(NOW.plusSeconds(300)), null))
        .expectError(IllegalArgumentException.class)
        .verify();
  }

  @Test
  void storeWithZeroTtlErrors() {
    StepVerifier.create(newAdapter().store(state(NOW.plusSeconds(300)), Duration.ZERO))
        .expectError(IllegalArgumentException.class)
        .verify();
  }

  @Test
  void storeSucceedsOnFirstAttempt() {
    givenRedisReady();
    when(references.generate()).thenReturn(reference);
    when(protector.protect(any(), any())).thenReturn(mock(ProtectedUsernameRecoveryStartReplay.class));
    when(json.toJson(any())).thenReturn("{}");
    when(values.setIfAbsent(any(), any(), any())).thenReturn(Mono.just(true));
    StepVerifier.create(newAdapter().store(state(NOW.plusSeconds(300)), Duration.ofMinutes(5)))
        .assertNext(r -> assertThat(r).isEqualTo(reference))
        .verifyComplete();
  }

  @Test
  void storeRetriesOnCollisionThenSucceeds() {
    givenRedisReady();
    when(references.generate()).thenReturn(reference);
    when(protector.protect(any(), any())).thenReturn(mock(ProtectedUsernameRecoveryStartReplay.class));
    when(json.toJson(any())).thenReturn("{}");
    when(values.setIfAbsent(any(), any(), any()))
        .thenReturn(Mono.just(false))
        .thenReturn(Mono.just(true));
    StepVerifier.create(newAdapter().store(state(NOW.plusSeconds(300)), Duration.ofMinutes(5)))
        .assertNext(r -> assertThat(r).isEqualTo(reference))
        .verifyComplete();
  }

  @Test
  void storeExhaustsCollisionAttemptsAndErrors() {
    givenRedisReady();
    when(references.generate()).thenReturn(reference);
    when(protector.protect(any(), any())).thenReturn(mock(ProtectedUsernameRecoveryStartReplay.class));
    when(json.toJson(any())).thenReturn("{}");
    when(values.setIfAbsent(any(), any(), any())).thenReturn(Mono.just(false));
    StepVerifier.create(newAdapter().store(state(NOW.plusSeconds(300)), Duration.ofMinutes(5)))
        .expectError(IllegalStateException.class)
        .verify();
  }

  // ----- resolve() : redis-level failures -----------------------------------------

  @Test
  void resolveMissingKeyErrors() {
    givenRedisReady();
    when(values.get(any())).thenReturn(Mono.empty());
    StepVerifier.create(newAdapter().resolve(reference))
        .expectError(IllegalArgumentException.class)
        .verify();
  }

  // ----- resolve() : authorizeAndHydrate branches ----------------------------------

  private void givenLoadedState(Instant replayExpiresAt) {
    givenRedisReady();
    when(values.get(any())).thenReturn(Mono.just("{}"));
    when(json.fromJson(any())).thenReturn(mock(ProtectedUsernameRecoveryStartReplay.class));
    when(protector.unprotect(any(), any())).thenReturn(state(replayExpiresAt));
  }

  @Test
  void expiredReplayStateErrors() {
    givenLoadedState(NOW.minusSeconds(1));
    StepVerifier.create(newAdapter().resolve(reference))
        .expectError(IllegalArgumentException.class)
        .verify();
  }

  @Test
  void sessionMissingErrors() {
    givenLoadedState(NOW.plusSeconds(300));
    when(sessions.loadSnapshot(any())).thenReturn(Mono.just(Optional.empty()));
    StepVerifier.create(newAdapter().resolve(reference))
        .expectError(IllegalArgumentException.class)
        .verify();
  }

  @Test
  void wrongScenarioErrors() {
    givenLoadedState(NOW.plusSeconds(300));
    when(sessions.loadSnapshot(any()))
        .thenReturn(
            Mono.just(
                Optional.of(
                    snapshotOf(sessionWith(AuthFlowScenario.LOGIN, AuthFlowStatus.STARTED, NOW.plusSeconds(300))))));
    StepVerifier.create(newAdapter().resolve(reference))
        .expectError(IllegalArgumentException.class)
        .verify();
  }

  @Test
  void wrongStatusErrors() {
    givenLoadedState(NOW.plusSeconds(300));
    when(sessions.loadSnapshot(any()))
        .thenReturn(
            Mono.just(
                Optional.of(
                    snapshotOf(
                        sessionWith(
                            AuthFlowScenario.USERNAME_RECOVERY, AuthFlowStatus.OTP_SENT,
                            NOW.plusSeconds(300))))));
    StepVerifier.create(newAdapter().resolve(reference))
        .expectError(IllegalArgumentException.class)
        .verify();
  }

  @Test
  void expiresAtMismatchErrors() {
    givenLoadedState(NOW.plusSeconds(300));
    when(sessions.loadSnapshot(any()))
        .thenReturn(
            Mono.just(
                Optional.of(
                    snapshotOf(
                        sessionWith(
                            AuthFlowScenario.USERNAME_RECOVERY, AuthFlowStatus.STARTED,
                            NOW.plusSeconds(999))))));
    StepVerifier.create(newAdapter().resolve(reference))
        .expectError(IllegalArgumentException.class)
        .verify();
  }

  // NOTE: there is intentionally no dedicated test for the session-level
  // `session.isExpired(now)` sub-condition inside the big OR-combined validity
  // check. Tracing the real authorizeAndHydrate source: the method's FIRST
  // guard already requires `state.expiresAt().isAfter(now)`, and the OR
  // condition separately requires `session.expiresAt().equals(state.expiresAt())`.
  // An already-past session.expiresAt() can never simultaneously equal a
  // still-future state.expiresAt() - so isolating "isExpired=true" while every
  // other sub-condition is false is a logical impossibility given the method's
  // own preconditions. This branch is effectively unreachable via the public
  // API (same category as the §9b/§9c dead-code findings recorded elsewhere in
  // docs/coverage/code-coverage-95-plan.md). The OR-condition overall is
  // already exercised via wrongScenarioErrors/wrongStatusErrors/
  // expiresAtMismatchErrors above.

  @Test
  void wrongWorkflowStatusErrors() {
    Instant expiry = NOW.plusSeconds(300);
    givenLoadedState(expiry);
    AuthFlowSession session = sessionWith(AuthFlowScenario.USERNAME_RECOVERY, AuthFlowStatus.STARTED, expiry);
    when(sessions.loadSnapshot(any())).thenReturn(Mono.just(Optional.of(snapshotOf(session))));
    UsernameRecoveryAuthFlowContext context = mock(UsernameRecoveryAuthFlowContext.class);
    when(context.workflowStatus()).thenReturn(UsernameRecoveryWorkflowStatus.COMPLETED);
    when(contexts.load(any())).thenReturn(context);
    StepVerifier.create(newAdapter().resolve(reference))
        .expectError(IllegalArgumentException.class)
        .verify();
  }

  @Test
  void happyPathYieldsSessionCreated() {
    Instant expiry = NOW.plusSeconds(300);
    givenLoadedState(expiry);
    AuthFlowSession session = sessionWith(AuthFlowScenario.USERNAME_RECOVERY, AuthFlowStatus.STARTED, expiry);
    when(sessions.loadSnapshot(any())).thenReturn(Mono.just(Optional.of(snapshotOf(session))));
    UsernameRecoveryAuthFlowContext context = mock(UsernameRecoveryAuthFlowContext.class);
    when(context.workflowStatus()).thenReturn(UsernameRecoveryWorkflowStatus.ACTIVE);
    // Real authorizeAndHydrate maps EVERY candidate directly (no filtering) into a
    // UsernameRecoverySchemeProjection(schemeRef, accountEnv, trustCode, registered) - confirmed
    // verbatim (round 57). UsernameRecoveryStartResult requires >=1 scheme projection.
    UsernameRecoverySessionCandidate candidate = mock(UsernameRecoverySessionCandidate.class);
    when(candidate.schemeRef()).thenReturn(mock(UsernameRecoverySchemeRef.class));
    when(candidate.accountEnv()).thenReturn("env1");
    when(candidate.trustCode()).thenReturn("trust1");
    when(candidate.registered()).thenReturn(true);
    when(context.candidates()).thenReturn(List.of(candidate));
    when(contexts.load(any())).thenReturn(context);
    when(csrf.createForSession(any()))
        .thenReturn(Mono.just(new CreateAuthFlowSessionCsrfResult("csrf-raw-token", expiry, 300)));
    StepVerifier.create(newAdapter().resolve(reference))
        .assertNext(
            r ->
                assertThat(r.outcome())
                    .isEqualTo(UsernameRecoveryStartResult.Outcome.SESSION_CREATED))
        .verifyComplete();
  }
}
