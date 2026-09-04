package com.bct.ngtpa.apiservice.adapter.out.redis.auth.registration.replay;

import com.bct.ngtpa.apiservice.application.idempotency.ProtectedReplayReference;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency.RegistrationStartProtectedReplayPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency.RegistrationStartProtectedReplayProtectorPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency.RegistrationStartProtectedReplayReferenceGenerator;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency.RegistrationStartProtectedReplayState;
import java.time.Duration;
import java.util.Objects;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;

public final class RedisRegistrationStartProtectedReplayAdapter
    implements RegistrationStartProtectedReplayPort {
  private static final int MAX_COLLISION_ATTEMPTS = 4;
  private final ReactiveStringRedisTemplate redis;
  private final RedisRegistrationStartProtectedReplayKeyFactory keys;
  private final RedisRegistrationStartProtectedReplayJsonMapper json;
  private final RegistrationStartProtectedReplayProtectorPort protector;
  private final RegistrationStartProtectedReplayReferenceGenerator references;

  public RedisRegistrationStartProtectedReplayAdapter(
      ReactiveStringRedisTemplate redis,
      RedisRegistrationStartProtectedReplayKeyFactory keys,
      RedisRegistrationStartProtectedReplayJsonMapper json,
      RegistrationStartProtectedReplayProtectorPort protector,
      RegistrationStartProtectedReplayReferenceGenerator references) {
    this.redis = Objects.requireNonNull(redis);
    this.keys = Objects.requireNonNull(keys);
    this.json = Objects.requireNonNull(json);
    this.protector = Objects.requireNonNull(protector);
    this.references = Objects.requireNonNull(references);
  }

  @Override
  public Mono<ProtectedReplayReference> store(
      RegistrationStartProtectedReplayState state, Duration ttl) {
    if (ttl == null || ttl.isZero() || ttl.isNegative())
      return Mono.error(new IllegalArgumentException("positive TTL required"));
    return storeAttempt(state, ttl, 1);
  }

  private Mono<ProtectedReplayReference> storeAttempt(
      RegistrationStartProtectedReplayState state, Duration ttl, int attempt) {
    ProtectedReplayReference reference = references.generate();
    String encoded = json.toJson(protector.protect(reference, state));
    return redis
        .opsForValue()
        .setIfAbsent(keys.keyFor(reference), encoded, ttl)
        .flatMap(
            saved ->
                Boolean.TRUE.equals(saved)
                    ? Mono.just(reference)
                    : attempt >= MAX_COLLISION_ATTEMPTS
                        ? Mono.error(
                            new IllegalStateException(
                                "Registration Start replay reference allocation failed"))
                        : storeAttempt(state, ttl, attempt + 1));
  }

  @Override
  public Mono<RegistrationStartProtectedReplayState> load(ProtectedReplayReference reference) {
    return redis
        .opsForValue()
        .get(keys.keyFor(reference))
        .switchIfEmpty(
            Mono.error(
                new IllegalArgumentException("Registration Start protected replay is unavailable")))
        .map(json::fromJson)
        .map(value -> protector.unprotect(reference, value));
  }
}
