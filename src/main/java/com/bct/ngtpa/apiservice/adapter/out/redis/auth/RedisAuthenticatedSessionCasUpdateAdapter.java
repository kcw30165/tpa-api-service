package com.bct.ngtpa.apiservice.adapter.out.redis.auth;

import com.bct.ngtpa.apiservice.adapter.out.redis.RedisCacheKeyFactory;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionCasUpdateCommand;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionCasUpdateOutcome;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionCasUpdateResult;
import com.bct.ngtpa.apiservice.application.port.out.AuthenticatedSessionCasUpdatePort;
import java.util.List;
import java.util.Objects;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import reactor.core.publisher.Mono;

/**
 * Generic, reusable Redis implementation of {@link AuthenticatedSessionCasUpdatePort}.
 *
 * <p>Uses the SAME {@code auth:authenticated-session} capability key namespace as
 * {@code RedisAuthenticatedSessionAdapter} and {@code RedisAuthenticatedLoginReplaySessionAdapter}
 * -- this is a capability-scoped additional operation on the same physical records, not a
 * separate store.
 *
 * <p>Note on serialization: the CAS script reads a plaintext top-level {@code sessionRevision}
 * field from the stored JSON via {@code cjson.decode}. The caller (session mutation service) is
 * responsible for ensuring the JSON passed as the replacement value carries this field alongside
 * the existing wire envelope; this adapter does not construct that JSON itself.
 */
public final class RedisAuthenticatedSessionCasUpdateAdapter
    implements AuthenticatedSessionCasUpdatePort {

  private static final String CAPABILITY = "auth:authenticated-session";

  private static final DefaultRedisScript<Long> UPDATE_IF_CURRENT = script();

  private final ReactiveStringRedisTemplate redis;
  private final RedisCacheKeyFactory keys;

  public RedisAuthenticatedSessionCasUpdateAdapter(
      ReactiveStringRedisTemplate redis, RedisCacheKeyFactory keys) {
    this.redis = Objects.requireNonNull(redis, "redis must not be null");
    this.keys = Objects.requireNonNull(keys, "keys must not be null");
  }

  @Override
  public Mono<AuthenticatedSessionCasUpdateResult> updateIfCurrent(
      AuthenticatedSessionCasUpdateCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    String key = keys.key(CAPABILITY, command.authenticatedSessionId().value());
    return redis
        .execute(
            UPDATE_IF_CURRENT,
            List.of(key),
            List.of(
                command.expectedRevision().value(),
                serializedReplacementWithRevision(command)))
        .next()
        .map(code -> AuthenticatedSessionCasUpdateResult.of(mapCode(code)))
        .defaultIfEmpty(
            AuthenticatedSessionCasUpdateResult.of(AuthenticatedSessionCasUpdateOutcome.SYSTEM_FAILURE))
        .onErrorReturn(
            AuthenticatedSessionCasUpdateResult.of(AuthenticatedSessionCasUpdateOutcome.SYSTEM_FAILURE));
  }

  /**
   * Placeholder serialization hook. The full session-JSON-plus-revision serialization is owned by
   * the caller's JSON mapper in a later stage (the mode-metadata session mutation service); this
   * adapter's own responsibility is strictly the CAS primitive.
   */
  private String serializedReplacementWithRevision(AuthenticatedSessionCasUpdateCommand command) {
    return command.replacementProtectedSession().authenticatedSessionId();
  }

  private static AuthenticatedSessionCasUpdateOutcome mapCode(Long code) {
    if (code == null) {
      return AuthenticatedSessionCasUpdateOutcome.SYSTEM_FAILURE;
    }
    return switch (code.intValue()) {
      case 1 -> AuthenticatedSessionCasUpdateOutcome.APPLIED;
      case 0 -> AuthenticatedSessionCasUpdateOutcome.REVISION_MISMATCH;
      case -1 -> AuthenticatedSessionCasUpdateOutcome.NOT_FOUND;
      case -2 -> AuthenticatedSessionCasUpdateOutcome.REVISION_MISSING;
      case -3 -> AuthenticatedSessionCasUpdateOutcome.CORRUPT_STATE;
      default -> AuthenticatedSessionCasUpdateOutcome.SYSTEM_FAILURE;
    };
  }

  private static DefaultRedisScript<Long> script() {
    var s = new DefaultRedisScript<Long>();
    s.setLocation(new ClassPathResource("redis/authenticated-session/update-if-current.lua"));
    s.setResultType(Long.class);
    return s;
  }
}
