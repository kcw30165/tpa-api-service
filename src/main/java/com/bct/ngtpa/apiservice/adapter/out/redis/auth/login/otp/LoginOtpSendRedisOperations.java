package com.bct.ngtpa.apiservice.adapter.out.redis.auth.login.otp;

import java.time.Duration;
import java.util.Optional;
import reactor.core.publisher.Mono;

interface LoginOtpSendRedisOperations {
  Mono<Boolean> setIfAbsent(String key, String value, Duration ttl);

  Mono<Void> set(String key, String value, Duration ttl);

  Mono<Optional<String>> get(String key);
}
