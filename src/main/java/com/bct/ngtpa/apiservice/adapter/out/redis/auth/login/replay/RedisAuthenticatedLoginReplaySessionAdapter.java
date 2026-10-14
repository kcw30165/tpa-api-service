package com.bct.ngtpa.apiservice.adapter.out.redis.auth.login.replay;

import com.bct.ngtpa.apiservice.adapter.out.redis.RedisCacheKeyFactory;
import com.bct.ngtpa.apiservice.adapter.out.redis.auth.RedisAuthenticatedSessionJsonMapper;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.*;
import com.bct.ngtpa.apiservice.application.port.out.AuthenticatedLoginReplaySessionPort;
import java.util.*;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;

/**
 * Read-only replay access to an already-committed authenticated session. The whole-value
 * compare-and-set previously exposed here ({@code rotateCsrfHashIfCurrent}) was removed: it
 * rejected any record whose encrypted attributes serialized in a different field order than the
 * live JVM's re-serialization, which made every committed-login replay fail with a false conflict
 * (see docs/design/12-csrf-token-refresh-conflict/README.md). CSRF rotation now goes through the
 * revision-based {@code AuthenticatedSessionConcurrencyService}.
 */
public final class RedisAuthenticatedLoginReplaySessionAdapter
    implements AuthenticatedLoginReplaySessionPort {
  private static final String CAPABILITY = "auth:authenticated-session";
  private final ReactiveStringRedisTemplate redis;
  private final RedisCacheKeyFactory keys;
  private final RedisAuthenticatedSessionJsonMapper json;

  public RedisAuthenticatedLoginReplaySessionAdapter(
      ReactiveStringRedisTemplate redis,
      RedisCacheKeyFactory keys,
      RedisAuthenticatedSessionJsonMapper json) {
    this.redis = Objects.requireNonNull(redis);
    this.keys = Objects.requireNonNull(keys);
    this.json = Objects.requireNonNull(json);
  }

  public Mono<Optional<ProtectedAuthenticatedSession>> loadForReplay(
      LoadAuthenticatedSessionForReplayCommand c) {
    return redis
        .opsForValue()
        .get(key(c.authenticatedSessionId()))
        .map(v -> Optional.of(json.fromJson(v)))
        .defaultIfEmpty(Optional.empty())
        .onErrorReturn(Optional.empty());
  }

  private String key(AuthenticatedSessionId id) {
    return keys.key(CAPABILITY, id.value());
  }
}
