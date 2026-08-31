package com.bct.ngtpa.apiservice.adapter.out.redis.auth;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import reactor.core.publisher.Mono;

class ReactiveRedisCaptchaChallengeOperations implements RedisCaptchaChallengeOperations {
  private static final RedisScript<Long> REPLACE_IF_UNCHANGED =
      RedisScript.of(
          "local current=redis.call('GET',KEYS[1]); "
              + "if not current then return -1 end; "
              + "if current~=ARGV[1] then return 0 end; "
              + "local ttl=redis.call('PTTL',KEYS[1]); if ttl<=0 then return -1 end; "
              + "redis.call('SET',KEYS[1],ARGV[2],'KEEPTTL'); return 1",
          Long.class);

  private final ReactiveStringRedisTemplate redisTemplate;

  ReactiveRedisCaptchaChallengeOperations(ReactiveStringRedisTemplate redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  @Override
  public Mono<Boolean> setIfAbsent(String key, String value, Duration ttl) {
    return redisTemplate.opsForValue().setIfAbsent(key, value, ttl).map(Boolean.TRUE::equals);
  }

  @Override
  public Mono<AtomicReplaceResult> replaceIfUnchanged(
      String key, String expectedValue, String replacementValue) {
    return redisTemplate
        .execute(REPLACE_IF_UNCHANGED, List.of(key), List.of(expectedValue, replacementValue))
        .next()
        .switchIfEmpty(
            Mono.error(
                new IllegalStateException(
                    "Captcha verification Redis script returned an unsupported result")))
        .flatMap(
            value -> {
              if (value == 1L) return Mono.just(AtomicReplaceResult.APPLIED);
              if (value == 0L) return Mono.just(AtomicReplaceResult.CONFLICT);
              if (value == -1L) return Mono.just(AtomicReplaceResult.MISSING);
              return Mono.error(
                  new IllegalStateException(
                      "Captcha verification Redis script returned an unsupported result"));
            });
  }

  @Override
  public Mono<Void> set(String key, String value, Duration ttl) {
    return redisTemplate.opsForValue().set(key, value, ttl).then();
  }

  @Override
  public Mono<Optional<String>> get(String key) {
    return redisTemplate.opsForValue().get(key).map(Optional::of).defaultIfEmpty(Optional.empty());
  }

  @Override
  public Mono<Optional<Duration>> ttl(String key) {
    return redisTemplate
        .getExpire(key)
        .map(
            seconds -> seconds == null || seconds.isNegative() || seconds.isZero() ? null : seconds)
        .map(Optional::ofNullable)
        .defaultIfEmpty(Optional.empty());
  }

  @Override
  public Mono<Void> delete(String key) {
    return redisTemplate.delete(key).then();
  }
}
