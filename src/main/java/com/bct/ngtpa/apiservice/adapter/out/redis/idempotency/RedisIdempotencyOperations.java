package com.bct.ngtpa.apiservice.adapter.out.redis.idempotency;

import java.time.Duration;
import java.util.Optional;
import reactor.core.publisher.Mono;

public interface RedisIdempotencyOperations {
  Mono<Boolean> setIfAbsent(String key, String value, Duration ttl);

  Mono<Optional<String>> get(String key);

  Mono<String> transition(String key, String expectedJson, String replacementJson);
}
