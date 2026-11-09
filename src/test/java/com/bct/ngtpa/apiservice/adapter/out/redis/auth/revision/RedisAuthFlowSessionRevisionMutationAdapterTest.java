package com.bct.ngtpa.apiservice.adapter.out.redis.auth.revision;

import static org.assertj.core.api.Assertions.*;

import com.bct.ngtpa.apiservice.adapter.in.web.session.config.AuthFlowSessionProperties;
import com.bct.ngtpa.apiservice.adapter.out.redis.RedisCacheKeyFactory;
import com.bct.ngtpa.apiservice.adapter.out.redis.auth.*;
import com.bct.ngtpa.apiservice.adapter.out.redis.auth.compatibility.AuthFlowSessionPersistenceCompatibilityState;
import com.bct.ngtpa.apiservice.adapter.out.session.AuthFlowSessionExpiryRefreshAdapter;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.*;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort;
import com.bct.ngtpa.apiservice.application.port.out.CachePort;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class RedisAuthFlowSessionRevisionMutationAdapterTest {
  private static final Instant NOW = Instant.parse("2026-08-01T00:00:00Z");

  @Test
  void successfulUpdateAdvancesRevisionAndPreservesExpiry() {
    var f = new Fixture();
    var expected = f.store();
    var updated = copy(expected.session(), AuthFlowStatus.OTP_VERIFIED, NOW.plusSeconds(30));
    var result =
        f.adapter
            .updateRevisioned(new RevisionedAuthFlowSessionMutationCommand(expected, updated))
            .block();
    assertThat(result.outcome()).isEqualTo(AuthFlowSessionMutationOutcome.APPLIED);
    assertThat(result.snapshot().revision()).isNotEqualTo(expected.revision());
    assertThat(result.snapshot().session().expiresAt()).isEqualTo(expected.session().expiresAt());
  }

  @Test
  void staleWriterAndDeleteConflictPerformZeroMutation() {
    var f = new Fixture();
    var expected = f.store();
    String before = f.cache.value;
    f.ops.code = 0;
    var result = f.adapter.touchRevisioned(expected, NOW.plusSeconds(10)).block();
    assertThat(result.outcome()).isEqualTo(AuthFlowSessionMutationOutcome.SESSION_REVISION_CHANGED);
    assertThat(f.cache.value).isEqualTo(before);
    var deleted =
        f.adapter.deleteRevisioned(new RevisionedAuthFlowSessionDeleteCommand(expected)).block();
    assertThat(deleted.outcome())
        .isEqualTo(AuthFlowSessionMutationOutcome.SESSION_REVISION_CHANGED);
    assertThat(f.cache.value).isEqualTo(before);
  }

  @Test
  void strictUpdateUsesRevisionCompareAndSet() {
    var f = new Fixture();
    var expected = f.store();
    String before = f.cache.value;
    f.ops.code = 0;

    var result =
        f.adapter
            .updateRevisionedStrict(
                new RevisionedAuthFlowSessionMutationCommand(
                    expected,
                    copy(expected.session(), AuthFlowStatus.COMPLETING, NOW.plusSeconds(5))))
            .block();

    assertThat(result.outcome()).isEqualTo(AuthFlowSessionMutationOutcome.SESSION_REVISION_CHANGED);
    assertThat(f.ops.replaceCalls).isOne();
    assertThat(f.cache.value).isEqualTo(before);
  }

  @Test
  void refreshCasCannotOverwriteConcurrentCompletingTransition() {
    var f = new Fixture();
    var sessionId = new AuthFlowSessionId("afs_test");
    var r1 = rev("a");
    var r2 = rev("c");
    f.store(registrationUsernameVerifiedSession(sessionId), r1);
    var completionTransitionResult = new AtomicReference<Long>();
    var refreshCasOutcome = new AtomicReference<AuthFlowSessionMutationOutcome>();

    f.ops.beforeNextReplace =
        () -> {
          var current = f.mapper.snapshotFromJson(f.cache.value).orElseThrow();
          assertThat(current.revision()).isEqualTo(r1);
          var completing = copy(current.session(), AuthFlowStatus.COMPLETING, NOW.plusSeconds(2));
          completionTransitionResult.set(
              f.ops.compareAndSet(r1.value(), f.mapper.toJson(completing, r2)));
        };

    var properties = new AuthFlowSessionProperties();
    properties.getScenarios().getRegistration().setTtlSeconds(1800);
    AuthFlowSessionPort observingPort = observeStrictOutcome(f.adapter, refreshCasOutcome);

    StepVerifier.create(
            new AuthFlowSessionExpiryRefreshAdapter(observingPort, properties)
                .refreshUsernameVerified(sessionId, NOW.plusSeconds(1)))
        .expectError(IllegalStateException.class)
        .verify();

    var stored = f.adapter.loadSnapshot(sessionId).block().orElseThrow();
    assertThat(completionTransitionResult.get()).isEqualTo(1L);
    assertThat(refreshCasOutcome.get())
        .isEqualTo(AuthFlowSessionMutationOutcome.SESSION_REVISION_CHANGED);
    assertThat(f.ops.replaceCalls).isOne();
    assertThat(f.ops.casAttempts).isEqualTo(2);
    assertThat(f.ops.lastCasResult).isZero();
    assertThat(f.cache.setCalls).isZero();
    assertThat(stored.session().status()).isEqualTo(AuthFlowStatus.COMPLETING);
    assertThat(stored.revision()).isEqualTo(r2);
  }

  @Test
  void terminalCompleteAndCancelAreRejectedWithoutRedisMutation() {
    var f = new Fixture();
    var terminal =
        new AuthFlowSessionSnapshot(copy(session(), AuthFlowStatus.COMPLETED, NOW), rev("a"));
    assertThat(f.adapter.completeRevisioned(terminal, NOW.plusSeconds(1)).block().outcome())
        .isEqualTo(AuthFlowSessionMutationOutcome.INVALID_TRANSITION);
    assertThat(f.adapter.cancelRevisioned(terminal, NOW.plusSeconds(1)).block().outcome())
        .isEqualTo(AuthFlowSessionMutationOutcome.INVALID_TRANSITION);
    assertThat(f.ops.replaceCalls).isZero();
  }

  private static SessionRevision rev(String c) {
    return new SessionRevision(c.repeat(32));
  }

  private static AuthFlowSession session() {
    return new AuthFlowSession(
        new AuthFlowSessionId("afs_test"),
        AuthFlowScenario.LOGIN,
        AuthFlowStatus.OTP_SENT,
        "csrf",
        "otp",
        AuthFlowSessionData.empty(),
        NOW,
        NOW,
        NOW.plusSeconds(900));
  }

  private static AuthFlowSession registrationUsernameVerifiedSession(AuthFlowSessionId id) {
    return new AuthFlowSession(
        id,
        AuthFlowScenario.REGISTRATION,
        AuthFlowStatus.USERNAME_VERIFIED,
        "csrf",
        "otp",
        AuthFlowSessionData.empty(),
        NOW.minusSeconds(30),
        NOW,
        NOW.plusSeconds(900));
  }

  private static AuthFlowSessionPort observeStrictOutcome(
      RedisAuthFlowSessionAdapter delegate,
      AtomicReference<AuthFlowSessionMutationOutcome> observedOutcome) {
    return new AuthFlowSessionPort() {
      @Override
      public Mono<AuthFlowSession> create(AuthFlowSessionCreateCommand command) {
        return delegate.create(command);
      }

      @Override
      public Mono<Optional<AuthFlowSession>> load(AuthFlowSessionId id) {
        return delegate.load(id);
      }

      @Override
      public Mono<Optional<AuthFlowSessionSnapshot>> loadSnapshot(AuthFlowSessionId id) {
        return delegate.loadSnapshot(id);
      }

      @Override
      public Mono<AuthFlowSessionMutationResult> updateRevisionedStrict(
          RevisionedAuthFlowSessionMutationCommand command) {
        return delegate
            .updateRevisionedStrict(command)
            .doOnNext(result -> observedOutcome.set(result.outcome()));
      }
    };
  }

  private static AuthFlowSession copy(AuthFlowSession s, AuthFlowStatus status, Instant updated) {
    return new AuthFlowSession(
        s.authFlowSessionId(),
        s.scenario(),
        status,
        s.csrfTokenHash(),
        s.activeOtpChallengeId(),
        s.data(),
        s.createdAt(),
        updated,
        s.expiresAt());
  }

  private static final class Fixture {
    final MemoryCache cache = new MemoryCache();
    final RedisAuthFlowSessionJsonMapper mapper = new RedisAuthFlowSessionJsonMapper();
    final Ops ops = new Ops(cache, mapper);
    final RedisAuthFlowSessionAdapter adapter;

    Fixture() {
      var state = new AuthFlowSessionPersistenceCompatibilityState();
      state.establish();
      adapter =
          new RedisAuthFlowSessionAdapter(
              cache,
              new RedisCacheKeyFactory("ngtpa"),
              mapper,
              ops,
              () -> rev("b"),
              state,
              Clock.fixed(NOW, ZoneOffset.UTC));
    }

    AuthFlowSessionSnapshot store() {
      return store(session(), rev("a"));
    }

    AuthFlowSessionSnapshot store(AuthFlowSession session, SessionRevision revision) {
      var snap = new AuthFlowSessionSnapshot(session, revision);
      cache.value = mapper.toJson(snap.session(), snap.revision());
      return snap;
    }
  }

  private static final class Ops implements RevisionedAuthFlowSessionRedisOperations {
    final MemoryCache cache;
    final RedisAuthFlowSessionJsonMapper mapper;
    long code = 1;
    int replaceCalls;
    int casAttempts;
    long lastCasResult = Long.MIN_VALUE;
    Runnable beforeNextReplace;

    Ops(MemoryCache c, RedisAuthFlowSessionJsonMapper mapper) {
      cache = c;
      this.mapper = mapper;
    }

    public Mono<Boolean> createIfAbsent(String k, String j, Duration t) {
      return Mono.just(false);
    }

    public Mono<Long> initializeLegacy(String a, String b, String c, String d, String e) {
      return Mono.just(-4L);
    }

    public synchronized Mono<Long> replaceIfCurrent(
        String k, String expectedRevision, String revised) {
      replaceCalls++;
      Runnable interleaving = beforeNextReplace;
      beforeNextReplace = null;
      if (interleaving != null) interleaving.run();
      if (code != 1) {
        lastCasResult = code;
        return Mono.just(code);
      }
      return Mono.just(compareAndSet(expectedRevision, revised));
    }

    synchronized long compareAndSet(String expectedRevision, String revised) {
      casAttempts++;
      if (cache.value == null) {
        lastCasResult = -1;
        return lastCasResult;
      }
      var current = mapper.snapshotFromJson(cache.value);
      if (current.isEmpty()) {
        lastCasResult = -2;
        return lastCasResult;
      }
      if (!current.orElseThrow().revision().value().equals(expectedRevision)) {
        lastCasResult = 0;
        return lastCasResult;
      }
      cache.value = revised;
      lastCasResult = 1;
      return lastCasResult;
    }

    public synchronized Mono<Long> deleteIfCurrent(String k, String expectedRevision) {
      if (code != 1) return Mono.just(code);
      return Mono.just(compareAndSet(expectedRevision, null));
    }
  }

  private static final class MemoryCache implements CachePort {
    String value;
    int setCalls;

    public Mono<Optional<String>> get(String k) {
      return Mono.just(Optional.ofNullable(value));
    }

    public Mono<Void> set(String k, String v, Duration t) {
      setCalls++;
      value = v;
      return Mono.empty();
    }

    public Mono<Boolean> evict(String k) {
      boolean x = value != null;
      value = null;
      return Mono.just(x);
    }
  }
}
