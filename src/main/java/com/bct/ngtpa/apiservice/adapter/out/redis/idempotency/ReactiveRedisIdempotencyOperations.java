package com.bct.ngtpa.apiservice.adapter.out.redis.idempotency;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import reactor.core.publisher.Mono;

public final class ReactiveRedisIdempotencyOperations implements RedisIdempotencyOperations {
  private static final RedisScript<String> CAS =
      RedisScript.of(
          "local v=redis.call('GET',KEYS[1]); if not v then return 'MISSING' end; "
              + "if v~=ARGV[1] then return 'CONFLICT' end; "
              + "local ttl=redis.call('PTTL',KEYS[1]); if ttl<=0 then return 'EXPIRED' end; "
              + "redis.call('SET',KEYS[1],ARGV[2],'PX',ttl); return ARGV[2]",
          String.class);
  private final ReactiveStringRedisTemplate redis;

  public ReactiveRedisIdempotencyOperations(ReactiveStringRedisTemplate redis) {
    this.redis = redis;
  }

  public Mono<Boolean> setIfAbsent(String key, String value, Duration ttl) {
    return redis.opsForValue().setIfAbsent(key, value, ttl).map(Boolean.TRUE::equals);
  }

  public Mono<Optional<String>> get(String key) {
    return redis.opsForValue().get(key).map(Optional::of).defaultIfEmpty(Optional.empty());
  }

  public Mono<String> transition(String key, String expectedJson, String replacementJson) {
    return redis.execute(CAS, List.of(key), List.of(expectedJson, replacementJson)).next();
  }
}
