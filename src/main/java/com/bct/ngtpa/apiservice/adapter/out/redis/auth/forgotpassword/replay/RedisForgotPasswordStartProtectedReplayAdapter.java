package com.bct.ngtpa.apiservice.adapter.out.redis.auth.forgotpassword.replay;

import com.bct.ngtpa.apiservice.application.idempotency.ProtectedReplayReference;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.start.ForgotPasswordStartProtectedReplayPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.start.ForgotPasswordStartProtectedReplayProtectorPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.start.ForgotPasswordStartProtectedReplayReferenceGenerator;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.start.ForgotPasswordStartProtectedReplayState;
import java.time.Duration;
import java.util.Objects;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;

public final class RedisForgotPasswordStartProtectedReplayAdapter
    implements ForgotPasswordStartProtectedReplayPort {
  private static final int MAX_COLLISION_ATTEMPTS = 4;
  private final ReactiveStringRedisTemplate redis;
  private final RedisForgotPasswordStartProtectedReplayKeyFactory keys;
  private final RedisForgotPasswordStartProtectedReplayJsonMapper json;
  private final ForgotPasswordStartProtectedReplayProtectorPort protector;
  private final ForgotPasswordStartProtectedReplayReferenceGenerator references;

  public RedisForgotPasswordStartProtectedReplayAdapter(
      ReactiveStringRedisTemplate redis,
      RedisForgotPasswordStartProtectedReplayKeyFactory keys,
      RedisForgotPasswordStartProtectedReplayJsonMapper json,
      ForgotPasswordStartProtectedReplayProtectorPort protector,
      ForgotPasswordStartProtectedReplayReferenceGenerator references) {
    this.redis = Objects.requireNonNull(redis);
    this.keys = Objects.requireNonNull(keys);
    this.json = Objects.requireNonNull(json);
    this.protector = Objects.requireNonNull(protector);
    this.references = Objects.requireNonNull(references);
  }

  @Override
  public Mono<ProtectedReplayReference> store(ForgotPasswordStartProtectedReplayState state, Duration ttl) {
    Objects.requireNonNull(state, "state must not be null");
    if (ttl == null || ttl.isZero() || ttl.isNegative()) {
      return Mono.error(new IllegalArgumentException("positive TTL required"));
    }
    return storeAttempt(state, ttl, 1);
  }

  private Mono<ProtectedReplayReference> storeAttempt(
      ForgotPasswordStartProtectedReplayState state, Duration ttl, int attempt) {
    ProtectedReplayReference reference = references.generate();
    String encoded = json.toJson(protector.protect(reference, state));
    return redis.opsForValue().setIfAbsent(keys.keyFor(reference), encoded, ttl)
        .flatMap(saved -> Boolean.TRUE.equals(saved)
            ? Mono.just(reference)
            : attempt >= MAX_COLLISION_ATTEMPTS
                ? Mono.error(new IllegalStateException("Forgot Password Start replay reference allocation failed"))
                : storeAttempt(state, ttl, attempt + 1));
  }

  @Override
  public Mono<ForgotPasswordStartProtectedReplayState> load(ProtectedReplayReference reference) {
    Objects.requireNonNull(reference, "reference must not be null");
    return redis.opsForValue().get(keys.keyFor(reference))
        .switchIfEmpty(Mono.error(new IllegalArgumentException("Forgot Password Start protected replay is unavailable")))
        .map(json::fromJson)
        .map(value -> protector.unprotect(reference, value));
  }
}
