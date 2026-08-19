package com.bct.ngtpa.apiservice.adapter.out.redis.auth.forgotpassword;

import com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordOtpCommitStatePort;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.ForgotPasswordOtpCommitState;
import java.time.Duration;
import java.util.Optional;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;

public final class RedisForgotPasswordOtpCommitStateAdapter
    implements ForgotPasswordOtpCommitStatePort {
  private final RedisForgotPasswordOtpCommitStateKeyFactory keys;
  private final RedisForgotPasswordOtpCommitStateJsonMapper mapper;
  private final ReactiveStringRedisTemplate redis;

  public RedisForgotPasswordOtpCommitStateAdapter(
      RedisForgotPasswordOtpCommitStateKeyFactory k,
      RedisForgotPasswordOtpCommitStateJsonMapper m,
      ReactiveStringRedisTemplate r) {
    keys = k;
    mapper = m;
    redis = r;
  }

  public Mono<Boolean> saveIfAbsent(String ref, ForgotPasswordOtpCommitState s, Duration ttl) {
    if (ttl == null || ttl.isZero() || ttl.isNegative())
      return Mono.error(new IllegalArgumentException("positive TTL required"));
    return redis
        .opsForValue()
        .setIfAbsent(keys.keyFor(ref), mapper.toJson(s), ttl)
        .map(Boolean.TRUE::equals);
  }

  public Mono<Optional<ForgotPasswordOtpCommitState>> load(String ref) {
    return redis
        .opsForValue()
        .get(keys.keyFor(ref))
        .map(mapper::fromJson)
        .map(Optional::of)
        .defaultIfEmpty(Optional.empty());
  }

  public Mono<Void> delete(String ref) {
    return redis.delete(keys.keyFor(ref)).then();
  }
}
