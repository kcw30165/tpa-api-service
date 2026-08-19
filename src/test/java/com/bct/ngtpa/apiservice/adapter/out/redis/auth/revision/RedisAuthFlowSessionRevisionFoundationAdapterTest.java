package com.bct.ngtpa.apiservice.adapter.out.redis.auth.revision;

import static org.assertj.core.api.Assertions.*;

import com.bct.ngtpa.apiservice.adapter.out.redis.RedisCacheKeyFactory;
import com.bct.ngtpa.apiservice.adapter.out.redis.auth.*;
import com.bct.ngtpa.apiservice.adapter.out.redis.auth.compatibility.AuthFlowSessionPersistenceCompatibilityState;
import com.bct.ngtpa.apiservice.application.dto.session.SessionLockHandle;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.*;
import com.bct.ngtpa.apiservice.application.port.out.CachePort;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class RedisAuthFlowSessionRevisionFoundationAdapterTest {
  private static final Instant NOW = Instant.parse("2026-08-01T00:00:00Z");

  @Test
  void createPersistsInitialRevisionInFirstWriteAndSnapshotLoadDoesNotAdvanceIt() {
    var fixture = new Fixture();
    var created = fixture.adapter.createRevisioned(command()).block();
    assertThat(created).isNotNull();
    assertThat(fixture.cache.value).contains("sessionRevision");
    var loaded = fixture.adapter.loadSnapshot(id()).block().orElseThrow();
    assertThat(loaded.revision()).isEqualTo(created.revision());
    assertThat(fixture.operations.createCalls).isEqualTo(1);
  }

  @Test
  void legacyInitializationRequiresExactRecordAndLockAndPreservesBusinessState() {
    var fixture = new Fixture();
    var legacy = session();
    fixture.cache.value = fixture.mapper.toJson(legacy);
    var result =
        fixture
            .adapter
            .initializeLegacyRevision(
                new InitializeLegacyAuthFlowSessionRevisionCommand(
                    legacy, new SessionLockHandle(true, "lock-key", "lock-token")))
            .block();
    assertThat(result.outcome())
        .isEqualTo(LegacyAuthFlowSessionRevisionInitializationOutcome.LEGACY_REVISION_INITIALIZED);
    assertThat(result.snapshot().session()).isEqualTo(legacy);
    assertThat(fixture.mapper.fromJson(fixture.cache.value)).isEqualTo(legacy);
  }

  @Test
  void changedLegacyRecordAndLostLockFailWithoutMutation() {
    var fixture = new Fixture();
    fixture.cache.value = fixture.mapper.toJson(session());
    fixture.operations.code = 0;
    String before = fixture.cache.value;
    var result =
        fixture
            .adapter
            .initializeLegacyRevision(
                new InitializeLegacyAuthFlowSessionRevisionCommand(
                    session(), new SessionLockHandle(true, "lock-key", "lock-token")))
            .block();
    assertThat(result.outcome())
        .isEqualTo(
            LegacyAuthFlowSessionRevisionInitializationOutcome.LEGACY_INITIALIZATION_CONFLICT);
    assertThat(fixture.cache.value).isEqualTo(before);
  }

  private static AuthFlowSessionCreateCommand command() {
    return new AuthFlowSessionCreateCommand(
        id(),
        AuthFlowScenario.LOGIN,
        AuthFlowStatus.OTP_SENT,
        NOW,
        NOW.plusSeconds(900),
        AuthFlowSessionData.empty(),
        "csrf",
        "otp");
  }

  private static AuthFlowSession session() {
    return new AuthFlowSession(
        id(),
        AuthFlowScenario.LOGIN,
        AuthFlowStatus.OTP_SENT,
        "csrf",
        "otp",
        AuthFlowSessionData.empty(),
        NOW,
        NOW,
        NOW.plusSeconds(900));
  }

  private static AuthFlowSessionId id() {
    return new AuthFlowSessionId("afs_test");
  }

  private static final class Fixture {
    final MemoryCache cache = new MemoryCache();
    final RedisAuthFlowSessionJsonMapper mapper = new RedisAuthFlowSessionJsonMapper();
    final FakeOperations operations = new FakeOperations(cache);
    final RedisAuthFlowSessionAdapter adapter;

    Fixture() {
      var state = new AuthFlowSessionPersistenceCompatibilityState();
      state.establish();
      adapter =
          new RedisAuthFlowSessionAdapter(
              cache,
              new RedisCacheKeyFactory("ngtpa"),
              mapper,
              operations,
              () -> new SessionRevision("0123456789abcdef0123456789abcdef"),
              state,
              Clock.fixed(NOW, ZoneOffset.UTC));
    }
  }

  private static final class FakeOperations implements RevisionedAuthFlowSessionRedisOperations {
    final MemoryCache cache;
    int createCalls;
    long code = 1;

    FakeOperations(MemoryCache c) {
      cache = c;
    }

    public Mono<Boolean> createIfAbsent(String k, String j, Duration t) {
      createCalls++;
      if (cache.value != null) return Mono.just(false);
      cache.value = j;
      cache.ttl = t;
      return Mono.just(true);
    }

    public Mono<Long> initializeLegacy(
        String k, String lk, String token, String expected, String revised) {
      if (code == 1 && Objects.equals(cache.value, expected)) cache.value = revised;
      return Mono.just(code);
    }

    public Mono<Long> replaceIfCurrent(String k, String expected, String revised) {
      if (code == 1 && Objects.equals(cache.value, expected)) cache.value = revised;
      return Mono.just(code);
    }

    public Mono<Long> deleteIfCurrent(String k, String expected) {
      if (code == 1 && Objects.equals(cache.value, expected)) cache.value = null;
      return Mono.just(code);
    }
  }

  private static final class MemoryCache implements CachePort {
    String value;
    Duration ttl;

    public Mono<Optional<String>> get(String k) {
      return Mono.just(Optional.ofNullable(value));
    }

    public Mono<Void> set(String k, String v, Duration t) {
      value = v;
      ttl = t;
      return Mono.empty();
    }

    public Mono<Boolean> evict(String k) {
      boolean x = value != null;
      value = null;
      return Mono.just(x);
    }
  }
}
