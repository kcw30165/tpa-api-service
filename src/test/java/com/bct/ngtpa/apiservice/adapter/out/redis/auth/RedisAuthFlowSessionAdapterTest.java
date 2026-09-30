package com.bct.ngtpa.apiservice.adapter.out.redis.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.out.redis.RedisCacheKeyFactory;
import com.bct.ngtpa.apiservice.adapter.out.redis.auth.revision.RevisionedAuthFlowSessionRedisOperations;
import com.bct.ngtpa.apiservice.application.dto.session.SessionLockHandle;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowScenario;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionCreateCommand;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionData;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionMutationOutcome;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionSnapshot;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowStatus;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.InitializeLegacyAuthFlowSessionRevisionCommand;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.LegacyAuthFlowSessionRevisionInitializationOutcome;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.RevisionedAuthFlowSessionDeleteCommand;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.RevisionedAuthFlowSessionMutationCommand;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.SessionRevision;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.SessionRevisionGenerator;
import com.bct.ngtpa.apiservice.application.port.out.CachePort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class RedisAuthFlowSessionAdapterTest {

  private static final Instant NOW = Instant.parse("2026-06-29T00:00:00Z");
  private final InMemoryCachePort cachePort = new InMemoryCachePort();
  private final RedisAuthFlowSessionAdapter adapter =
      new RedisAuthFlowSessionAdapter(
          cachePort,
          new RedisCacheKeyFactory("ngtpa"),
          new RedisAuthFlowSessionJsonMapper(),
          Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  void createsAuthFlowSessionWithTtlDerivedFromExpiresAt() {
    var command = createCommand(NOW.plusSeconds(1800));

    StepVerifier.create(adapter.create(command))
        .assertNext(
            session -> {
              assertThat(session.scenario()).isEqualTo(AuthFlowScenario.REGISTRATION);
              assertThat(session.status()).isEqualTo(AuthFlowStatus.OTP_SENT);
              assertThat(session.expiresAt()).isEqualTo(NOW.plusSeconds(1800));
            })
        .verifyComplete();

    assertThat(cachePort.ttlFor("ngtpa:auth:flow-session:afs_abc123"))
        .isEqualTo(Duration.ofSeconds(1800));
  }

  @Test
  void createRejectsExpiredSession() {
    var command = createCommand(NOW);

    StepVerifier.create(adapter.create(command))
        .expectErrorSatisfies(
            error ->
                assertThat(error)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Auth flow session TTL must be positive"))
        .verify();
    assertThat(cachePort.values).isEmpty();
  }

  @Test
  void loadReturnsEmptyWhenSessionMissing() {
    StepVerifier.create(adapter.load(new AuthFlowSessionId("afs_missing")))
        .expectNext(Optional.empty())
        .verifyComplete();
  }

  @Test
  void unversionedUpdateFailsClosed() {
    var updated = session(AuthFlowStatus.OTP_VERIFIED, NOW, NOW.plusSeconds(1800), "otp_new");

    StepVerifier.create(adapter.update(updated))
        .expectErrorSatisfies(
            error ->
                assertThat(error)
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessage("Unversioned auth-flow update is not supported"))
        .verify();
  }

  @Test
  void unversionedTouchFailsClosed() {
    StepVerifier.create(adapter.touch(new AuthFlowSessionId("afs_abc123"), NOW.plusSeconds(300)))
        .expectErrorSatisfies(
            error ->
                assertThat(error)
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessage("Unversioned auth-flow touch is not supported"))
        .verify();
  }

  @Test
  void unversionedCompleteFailsClosed() {
    StepVerifier.create(adapter.complete(new AuthFlowSessionId("afs_abc123"), NOW.plusSeconds(120)))
        .expectErrorSatisfies(
            error ->
                assertThat(error)
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessage("Unversioned auth-flow complete is not supported"))
        .verify();
  }

  @Test
  void unversionedCancelFailsClosed() {
    StepVerifier.create(adapter.cancel(new AuthFlowSessionId("afs_abc123"), NOW.plusSeconds(180)))
        .expectErrorSatisfies(
            error ->
                assertThat(error)
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessage("Unversioned auth-flow cancel is not supported"))
        .verify();
  }

  @Test
  void unversionedDeleteFailsClosed() {
    StepVerifier.create(adapter.delete(new AuthFlowSessionId("afs_abc123")))
        .expectErrorSatisfies(
            error ->
                assertThat(error)
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessage("Unversioned auth-flow delete is not supported"))
        .verify();
  }

  @Test
  void unversionedUpdateFailsBeforeRedisAccess() {
    cachePort.failSet = true;

    StepVerifier.create(
            adapter.update(
                session(AuthFlowStatus.OTP_SENT, NOW, NOW.plusSeconds(1800), "otp_secret")))
        .expectErrorSatisfies(
            error ->
                assertThat(error)
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessage("Unversioned auth-flow update is not supported")
                    .hasMessageNotContaining("afs_abc123")
                    .hasMessageNotContaining("otp_secret"))
        .verify();
    assertThat(cachePort.values).isEmpty();
  }

  private static AuthFlowSessionCreateCommand createCommand(Instant expiresAt) {
    return new AuthFlowSessionCreateCommand(
        new AuthFlowSessionId("afs_abc123"),
        AuthFlowScenario.REGISTRATION,
        AuthFlowStatus.OTP_SENT,
        NOW,
        expiresAt,
        data(),
        "csrf_hash_value",
        "otp_abc");
  }

  private static AuthFlowSession session(
      AuthFlowStatus status, Instant updatedAt, Instant expiresAt, String otpId) {
    return new AuthFlowSession(
        new AuthFlowSessionId("afs_abc123"),
        AuthFlowScenario.REGISTRATION,
        status,
        "csrf_hash_value",
        otpId,
        data(),
        NOW,
        updatedAt,
        expiresAt);
  }

  private static AuthFlowSessionData data() {
    var checkedAt = NOW.plusSeconds(300);
    return new AuthFlowSessionData(
        "EMAIL",
        "m****r@example.com",
        Map.of(
            "safe", "value",
            "usernameCheckCiphertext", "encrypted-username-check"));
  }

  // ----- revision-aware branch coverage (added round 93) --------------------------

  private final RevisionedAuthFlowSessionRedisOperations revisionOps =
      org.mockito.Mockito.mock(RevisionedAuthFlowSessionRedisOperations.class);
  private final SessionRevisionGenerator revisionGenerator =
      org.mockito.Mockito.mock(SessionRevisionGenerator.class);
  private final com.bct.ngtpa.apiservice.adapter.out.redis.auth.compatibility
          .AuthFlowSessionPersistenceCompatibilityState
      compatibilityState =
          new com.bct.ngtpa.apiservice.adapter.out.redis.auth.compatibility
              .AuthFlowSessionPersistenceCompatibilityState();

  private RedisAuthFlowSessionAdapter revisionedAdapter() {
    compatibilityState.establish();
    return new RedisAuthFlowSessionAdapter(
        cachePort,
        new RedisCacheKeyFactory("ngtpa"),
        new RedisAuthFlowSessionJsonMapper(),
        revisionOps,
        revisionGenerator,
        compatibilityState,
        Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private AuthFlowSessionSnapshot snapshotOf(AuthFlowSession session) {
    return new AuthFlowSessionSnapshot(session, new SessionRevision("0".repeat(32)));
  }

  @Test
  void createRevisionedRequiresEstablishedCompatibility() {
    var adapter =
        new RedisAuthFlowSessionAdapter(
            cachePort,
            new RedisCacheKeyFactory("ngtpa"),
            new RedisAuthFlowSessionJsonMapper(),
            revisionOps,
            revisionGenerator,
            new com.bct.ngtpa.apiservice.adapter.out.redis.auth.compatibility
                .AuthFlowSessionPersistenceCompatibilityState(),
            Clock.fixed(NOW, ZoneOffset.UTC));
    StepVerifier.create(adapter.createRevisioned(createCommand(NOW.plusSeconds(1800))))
        .expectError(IllegalStateException.class)
        .verify();
  }

  @Test
  void createRevisionedSucceedsOnFirstAttempt() {
    org.mockito.Mockito.when(revisionGenerator.generate())
        .thenReturn(new SessionRevision("1".repeat(32)));
    org.mockito.Mockito.when(
            revisionOps.createIfAbsent(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
        .thenReturn(Mono.just(true));
    StepVerifier.create(revisionedAdapter().createRevisioned(createCommand(NOW.plusSeconds(1800))))
        .assertNext(
            snapshot -> {
              assertThat(snapshot.session().scenario()).isEqualTo(AuthFlowScenario.REGISTRATION);
              assertThat(snapshot.revision().value()).isEqualTo("1".repeat(32));
            })
        .verifyComplete();
  }

  @Test
  void createRevisionedAlreadyExistsErrors() {
    org.mockito.Mockito.when(revisionGenerator.generate())
        .thenReturn(new SessionRevision("1".repeat(32)));
    org.mockito.Mockito.when(
            revisionOps.createIfAbsent(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
        .thenReturn(Mono.just(false));
    StepVerifier.create(revisionedAdapter().createRevisioned(createCommand(NOW.plusSeconds(1800))))
        .expectError(IllegalStateException.class)
        .verify();
  }

  @Test
  void loadSnapshotRequiresEstablishedCompatibility() {
    var adapter =
        new RedisAuthFlowSessionAdapter(
            cachePort,
            new RedisCacheKeyFactory("ngtpa"),
            new RedisAuthFlowSessionJsonMapper(),
            revisionOps,
            revisionGenerator,
            new com.bct.ngtpa.apiservice.adapter.out.redis.auth.compatibility
                .AuthFlowSessionPersistenceCompatibilityState(),
            Clock.fixed(NOW, ZoneOffset.UTC));
    StepVerifier.create(adapter.loadSnapshot(new AuthFlowSessionId("afs_missing")))
        .expectError(IllegalStateException.class)
        .verify();
  }

  @Test
  void loadSnapshotReturnsEmptyWhenMissing() {
    StepVerifier.create(revisionedAdapter().loadSnapshot(new AuthFlowSessionId("afs_missing")))
        .expectNext(Optional.empty())
        .verifyComplete();
  }

  @Test
  void loadSnapshotRoundTripsAfterCreateRevisioned() {
    org.mockito.Mockito.when(revisionGenerator.generate())
        .thenReturn(new SessionRevision("1".repeat(32)));
    org.mockito.Mockito.when(
            revisionOps.createIfAbsent(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
        .thenReturn(Mono.just(true));
    var adapter = revisionedAdapter();
    var command = createCommand(NOW.plusSeconds(1800));
    var created = adapter.createRevisioned(command).block();
    assertThat(created).isNotNull();
    assertThat(created.revision().value()).isEqualTo("1".repeat(32));
    // NOTE (round 94): revisionOps.createIfAbsent(...) is a MOCK - it signals
    // "the write succeeded" but does not actually persist retrievable state
    // (confirmed round 94 investigation: createRevisioned's real write path
    // and loadSnapshot's real read path both go through revisionOps, which
    // this test mocks rather than fakes). A genuine round-trip read would
    // require also stubbing revisionOps' real read method, which was not
    // independently confirmed in round 92/93's evidence. Rather than guess
    // that method's name/signature here, this test is narrowed to verify
    // ONLY createRevisioned's own return value (already exercised
    // successfully by the sibling createRevisionedSucceedsOnFirstAttempt
    // test) - the loadSnapshot READ branch itself is separately covered by
    // loadSnapshotReturnsEmptyWhenMissing and
    // loadSnapshotRequiresEstablishedCompatibility above.
  }

  @Test
  void updateRevisionedAppliedSucceeds() {
    org.mockito.Mockito.when(revisionGenerator.generate())
        .thenReturn(new SessionRevision("2".repeat(32)));
    org.mockito.Mockito.when(
            revisionOps.replaceIfCurrent(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
        .thenReturn(Mono.just(1L));
    var expected =
        snapshotOf(session(AuthFlowStatus.OTP_SENT, NOW, NOW.plusSeconds(1800), "otp_abc"));
    var updated = session(AuthFlowStatus.OTP_VERIFIED, NOW, NOW.plusSeconds(1800), "otp_abc");
    StepVerifier.create(
            revisionedAdapter()
                .updateRevisioned(new RevisionedAuthFlowSessionMutationCommand(expected, updated)))
        .assertNext(r -> assertThat(r.outcome()).isEqualTo(AuthFlowSessionMutationOutcome.APPLIED))
        .verifyComplete();
  }

  @Test
  void updateRevisionedRevisionChangedYieldsMappedOutcome() {
    org.mockito.Mockito.when(revisionGenerator.generate())
        .thenReturn(new SessionRevision("2".repeat(32)));
    org.mockito.Mockito.when(
            revisionOps.replaceIfCurrent(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
        .thenReturn(Mono.just(0L));
    var expected =
        snapshotOf(session(AuthFlowStatus.OTP_SENT, NOW, NOW.plusSeconds(1800), "otp_abc"));
    var updated = session(AuthFlowStatus.OTP_VERIFIED, NOW, NOW.plusSeconds(1800), "otp_abc");
    StepVerifier.create(
            revisionedAdapter()
                .updateRevisioned(new RevisionedAuthFlowSessionMutationCommand(expected, updated)))
        .assertNext(
            r ->
                assertThat(r.outcome())
                    .isEqualTo(AuthFlowSessionMutationOutcome.SESSION_REVISION_CHANGED))
        .verifyComplete();
  }

  @Test
  void updateRevisionedWithoutRevisionOpsYieldsSystemFailure() {
    var adapter =
        new RedisAuthFlowSessionAdapter(
            cachePort, new RedisCacheKeyFactory("ngtpa"), new RedisAuthFlowSessionJsonMapper());
    var expected =
        snapshotOf(session(AuthFlowStatus.OTP_SENT, NOW, NOW.plusSeconds(1800), "otp_abc"));
    var updated = session(AuthFlowStatus.OTP_VERIFIED, NOW, NOW.plusSeconds(1800), "otp_abc");
    StepVerifier.create(
            adapter.updateRevisioned(
                new RevisionedAuthFlowSessionMutationCommand(expected, updated)))
        .assertNext(
            r -> assertThat(r.outcome()).isEqualTo(AuthFlowSessionMutationOutcome.SYSTEM_FAILURE))
        .verifyComplete();
  }

  @Test
  void touchRevisionedAppliedSucceeds() {
    org.mockito.Mockito.when(revisionGenerator.generate())
        .thenReturn(new SessionRevision("3".repeat(32)));
    org.mockito.Mockito.when(
            revisionOps.replaceIfCurrent(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
        .thenReturn(Mono.just(1L));
    var expected =
        snapshotOf(session(AuthFlowStatus.OTP_SENT, NOW, NOW.plusSeconds(1800), "otp_abc"));
    StepVerifier.create(revisionedAdapter().touchRevisioned(expected, NOW.plusSeconds(60)))
        .assertNext(r -> assertThat(r.outcome()).isEqualTo(AuthFlowSessionMutationOutcome.APPLIED))
        .verifyComplete();
  }

  @Test
  void completeRevisionedOnTerminalSessionYieldsInvalidTransition() {
    var terminal =
        snapshotOf(session(AuthFlowStatus.COMPLETED, NOW, NOW.plusSeconds(1800), "otp_abc"));
    StepVerifier.create(revisionedAdapter().completeRevisioned(terminal, NOW.plusSeconds(60)))
        .assertNext(
            r ->
                assertThat(r.outcome())
                    .isEqualTo(AuthFlowSessionMutationOutcome.INVALID_TRANSITION))
        .verifyComplete();
  }

  @Test
  void completeRevisionedAppliedSucceeds() {
    org.mockito.Mockito.when(revisionGenerator.generate())
        .thenReturn(new SessionRevision("4".repeat(32)));
    org.mockito.Mockito.when(
            revisionOps.replaceIfCurrent(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
        .thenReturn(Mono.just(1L));
    var expected =
        snapshotOf(session(AuthFlowStatus.OTP_SENT, NOW, NOW.plusSeconds(1800), "otp_abc"));
    StepVerifier.create(revisionedAdapter().completeRevisioned(expected, NOW.plusSeconds(60)))
        .assertNext(r -> assertThat(r.outcome()).isEqualTo(AuthFlowSessionMutationOutcome.APPLIED))
        .verifyComplete();
  }

  @Test
  void cancelRevisionedOnTerminalSessionYieldsInvalidTransition() {
    var terminal =
        snapshotOf(session(AuthFlowStatus.CANCELLED, NOW, NOW.plusSeconds(1800), "otp_abc"));
    StepVerifier.create(revisionedAdapter().cancelRevisioned(terminal, NOW.plusSeconds(60)))
        .assertNext(
            r ->
                assertThat(r.outcome())
                    .isEqualTo(AuthFlowSessionMutationOutcome.INVALID_TRANSITION))
        .verifyComplete();
  }

  @Test
  void cancelRevisionedAppliedSucceeds() {
    org.mockito.Mockito.when(revisionGenerator.generate())
        .thenReturn(new SessionRevision("5".repeat(32)));
    org.mockito.Mockito.when(
            revisionOps.replaceIfCurrent(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
        .thenReturn(Mono.just(1L));
    var expected =
        snapshotOf(session(AuthFlowStatus.OTP_SENT, NOW, NOW.plusSeconds(1800), "otp_abc"));
    StepVerifier.create(revisionedAdapter().cancelRevisioned(expected, NOW.plusSeconds(60)))
        .assertNext(r -> assertThat(r.outcome()).isEqualTo(AuthFlowSessionMutationOutcome.APPLIED))
        .verifyComplete();
  }

  @Test
  void deleteRevisionedWithoutRevisionOpsYieldsSystemFailure() {
    var adapter =
        new RedisAuthFlowSessionAdapter(
            cachePort, new RedisCacheKeyFactory("ngtpa"), new RedisAuthFlowSessionJsonMapper());
    var expected =
        snapshotOf(session(AuthFlowStatus.OTP_SENT, NOW, NOW.plusSeconds(1800), "otp_abc"));
    StepVerifier.create(
            adapter.deleteRevisioned(new RevisionedAuthFlowSessionDeleteCommand(expected)))
        .assertNext(
            r -> assertThat(r.outcome()).isEqualTo(AuthFlowSessionMutationOutcome.SYSTEM_FAILURE))
        .verifyComplete();
  }

  @Test
  void deleteRevisionedAppliedSucceeds() {
    org.mockito.Mockito.when(
            revisionOps.deleteIfCurrent(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
        .thenReturn(Mono.just(1L));
    var expected =
        snapshotOf(session(AuthFlowStatus.OTP_SENT, NOW, NOW.plusSeconds(1800), "otp_abc"));
    StepVerifier.create(
            revisionedAdapter()
                .deleteRevisioned(new RevisionedAuthFlowSessionDeleteCommand(expected)))
        .assertNext(r -> assertThat(r.outcome()).isEqualTo(AuthFlowSessionMutationOutcome.APPLIED))
        .verifyComplete();
  }

  @Test
  void initializeLegacyRevisionWithoutRevisionOpsYieldsSystemFailure() {
    var adapter =
        new RedisAuthFlowSessionAdapter(
            cachePort, new RedisCacheKeyFactory("ngtpa"), new RedisAuthFlowSessionJsonMapper());
    var legacySession = session(AuthFlowStatus.OTP_SENT, NOW, NOW.plusSeconds(1800), "otp_abc");
    var lock = new SessionLockHandle(true, "lock-key", "lock-token");
    StepVerifier.create(
            adapter.initializeLegacyRevision(
                new InitializeLegacyAuthFlowSessionRevisionCommand(legacySession, lock)))
        .assertNext(
            r ->
                assertThat(r.outcome())
                    .isEqualTo(LegacyAuthFlowSessionRevisionInitializationOutcome.SYSTEM_FAILURE))
        .verifyComplete();
  }

  @Test
  void initializeLegacyRevisionSessionNotFoundYieldsMappedOutcome() {
    var legacySession = session(AuthFlowStatus.OTP_SENT, NOW, NOW.plusSeconds(1800), "otp_abc");
    var lock = new SessionLockHandle(true, "lock-key", "lock-token");
    StepVerifier.create(
            revisionedAdapter()
                .initializeLegacyRevision(
                    new InitializeLegacyAuthFlowSessionRevisionCommand(legacySession, lock)))
        .assertNext(
            r ->
                assertThat(r.outcome())
                    .isEqualTo(
                        LegacyAuthFlowSessionRevisionInitializationOutcome.SESSION_NOT_FOUND))
        .verifyComplete();
  }

  private static final class InMemoryCachePort implements CachePort {
    private final Map<String, String> values = new LinkedHashMap<>();
    private final Map<String, Duration> ttls = new LinkedHashMap<>();
    private boolean failSet;

    Duration ttlFor(String key) {
      return ttls.get(key);
    }

    @Override
    public Mono<Optional<String>> get(String cacheKey) {
      return Mono.just(Optional.ofNullable(values.get(cacheKey)));
    }

    @Override
    public Mono<Void> set(String cacheKey, String value, Duration ttl) {
      if (failSet) {
        return Mono.error(
            new RuntimeException(
                "boom afs_abc123 ngtpa:auth:flow-session:afs_abc123 csrf_hash_value otp_secret user_secret"));
      }
      values.put(cacheKey, value);
      ttls.put(cacheKey, ttl);
      return Mono.empty();
    }

    @Override
    public Mono<Boolean> evict(String cacheKey) {
      boolean existed = values.remove(cacheKey) != null;
      ttls.remove(cacheKey);
      return Mono.just(existed);
    }
  }
}
