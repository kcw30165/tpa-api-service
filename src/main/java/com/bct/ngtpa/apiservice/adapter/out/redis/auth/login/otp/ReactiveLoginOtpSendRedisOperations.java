package com.bct.ngtpa.apiservice.adapter.out.redis.auth.login.otp;

import java.time.Duration;
import java.util.Optional;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;

final class ReactiveLoginOtpSendRedisOperations implements LoginOtpSendRedisOperations {
  private final ReactiveStringRedisTemplate redis;

  ReactiveLoginOtpSendRedisOperations(ReactiveStringRedisTemplate redis) {
    this.redis = redis;
  }

  public Mono<Boolean> setIfAbsent(String k, String v, Duration t) {
    return redis.opsForValue().setIfAbsent(k, v, t).map(Boolean.TRUE::equals);
  }

  public Mono<Void> set(String k, String v, Duration t) {
    return redis.opsForValue().set(k, v, t).then();
  }

  public Mono<Optional<String>> get(String k) {
    return redis.opsForValue().get(k).map(Optional::of).defaultIfEmpty(Optional.empty());
  }
}
