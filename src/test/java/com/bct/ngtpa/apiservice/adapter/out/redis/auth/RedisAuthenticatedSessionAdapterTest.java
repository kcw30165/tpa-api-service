package com.bct.ngtpa.apiservice.adapter.out.redis.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.out.redis.RedisCacheKeyFactory;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.*;
import com.bct.ngtpa.apiservice.application.port.out.CachePort;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class RedisAuthenticatedSessionAdapterTest {
  private static final Instant NOW = Instant.parse("2026-07-20T00:00:00Z");
  private final MemoryCache cache = new MemoryCache();
  private final RedisAuthenticatedSessionAdapter adapter =
      new RedisAuthenticatedSessionAdapter(
          cache, new RedisCacheKeyFactory("ngtpa"), new RedisAuthenticatedSessionJsonMapper());
  private final AuthenticatedSessionId id =
      new AuthenticatedSessionId("sid_abcdefghijklmnopqrstuvwx");
  private final ProtectedAuthenticatedSession payload =
      com.bct.ngtpa.apiservice.application.dto.session.authenticated
          .ProtectedAuthenticatedSessionTestFixtures.envelope(
          "v1", "cipher-secret", "nonce-secret", "tag-secret");

  @Test
  void storeUsesCapabilityScopedOpaqueKeyAndCallerOwnedTtl() {
    StepVerifier.create(
            adapter.store(
                new StoreAuthenticatedSessionCommand(
                    id, payload, Duration.ofMinutes(30), NOW, "req")))
        .assertNext(
            r -> assertThat(r.status()).isEqualTo(AuthenticatedSessionPersistenceStatus.STORED))
        .verifyComplete();
    assertThat(cache.values)
        .containsKey("ngtpa:auth:authenticated-session:sid_abcdefghijklmnopqrstuvwx");
    assertThat(cache.ttls.get("ngtpa:auth:authenticated-session:sid_abcdefghijklmnopqrstuvwx"))
        .isEqualTo(Duration.ofMinutes(30));
    assertThat(cache.values.values().iterator().next())
        .doesNotContain("actorUserId", "policyNo", "csrfTokenHash");
  }

  @Test
  void loadReturnsProtectedEnvelopeAndMissingIsEmpty() {
    adapter
        .store(
            new StoreAuthenticatedSessionCommand(id, payload, Duration.ofMinutes(30), NOW, "req"))
        .block();
    StepVerifier.create(adapter.load(new LoadAuthenticatedSessionCommand(id, "req")))
        .assertNext(v -> assertThat(v).contains(payload))
        .verifyComplete();
    StepVerifier.create(
            adapter.load(
                new LoadAuthenticatedSessionCommand(
                    new AuthenticatedSessionId("sid_yxwvutsrqponmlkjihgfedcb"), "req")))
        .expectNext(Optional.empty())
        .verifyComplete();
  }

  @Test
  void touchAtomicallyReplacesProtectedValueAndTtl() {
    var next =
        com.bct.ngtpa.apiservice.application.dto.session.authenticated
            .ProtectedAuthenticatedSessionTestFixtures.envelope(
            "v1", "cipher-next", "nonce-next", "tag-next");
    StepVerifier.create(
            adapter.touch(
                new TouchAuthenticatedSessionCommand(id, next, Duration.ofMinutes(10), NOW, "req")))
        .assertNext(
            r -> assertThat(r.status()).isEqualTo(AuthenticatedSessionPersistenceStatus.TOUCHED))
        .verifyComplete();
    assertThat(cache.ttls.values()).containsExactly(Duration.ofMinutes(10));
  }

  @Test
  void revokeAndDeleteAreIdempotentKeyRemoval() {
    adapter
        .store(
            new StoreAuthenticatedSessionCommand(id, payload, Duration.ofMinutes(30), NOW, "req"))
        .block();
    StepVerifier.create(
            adapter.revoke(new RevokeAuthenticatedSessionCommand(id, NOW, "LOGOUT", "req")))
        .assertNext(
            r -> assertThat(r.status()).isEqualTo(AuthenticatedSessionPersistenceStatus.REVOKED))
        .verifyComplete();
    StepVerifier.create(adapter.delete(new DeleteAuthenticatedSessionCommand(id, "req")))
        .assertNext(
            r -> assertThat(r.status()).isEqualTo(AuthenticatedSessionPersistenceStatus.NOT_FOUND))
        .verifyComplete();
  }

  static final class MemoryCache implements CachePort {
    final Map<String, String> values = new HashMap<>();
    final Map<String, Duration> ttls = new HashMap<>();

    public Mono<Void> set(String k, String v, Duration ttl) {
      values.put(k, v);
      ttls.put(k, ttl);
      return Mono.empty();
    }

    public Mono<Optional<String>> get(String k) {
      return Mono.just(Optional.ofNullable(values.get(k)));
    }

    public Mono<Boolean> evict(String k) {
      boolean removed = values.remove(k) != null;
      ttls.remove(k);
      return Mono.just(removed);
    }
  }
}
