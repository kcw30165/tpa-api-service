package com.bct.ngtpa.apiservice.adapter.out.redis.metadata;

import com.bct.ngtpa.apiservice.application.dto.metadata.GeneralMetadataCacheReplaceResult;
import com.bct.ngtpa.apiservice.exception.CacheException;
import java.util.List;
import java.util.Objects;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
final class ReactiveGeneralMetadataRedisOperations implements GeneralMetadataRedisOperations {
  private static final String SCRIPT_TEXT =
      "local current=redis.call('GET',KEYS[1]); "
          + "if not current then return -1 end; "
          + "if current~=ARGV[1] then return 0 end; "
          + "local ttl=redis.call('PTTL',KEYS[1]); "
          + "if ttl<=0 then return -1 end; "
          + "redis.call('SET',KEYS[1],ARGV[2],'KEEPTTL'); return 1";
  private static final RedisScript<Long> SCRIPT = RedisScript.of(SCRIPT_TEXT, Long.class);
  private final ReactiveStringRedisTemplate redis;

  ReactiveGeneralMetadataRedisOperations(ReactiveStringRedisTemplate redis) {
    this.redis = Objects.requireNonNull(redis, "redis must not be null");
  }

  @Override
  public Mono<GeneralMetadataCacheReplaceResult> replaceIfUnchanged(
      String key, String expected, String replacement) {
    Objects.requireNonNull(key, "key must not be null");
    Objects.requireNonNull(expected, "expected must not be null");
    Objects.requireNonNull(replacement, "replacement must not be null");
    return redis
        .execute(SCRIPT, List.of(key), List.of(expected, replacement))
        .singleOrEmpty()
        .switchIfEmpty(Mono.error(new CacheException("Cache replace operation failed")))
        .map(this::mapResult)
        .onErrorMap(
            error ->
                error instanceof CacheException
                    ? error
                    : new CacheException("Cache replace operation failed", error));
  }

  private GeneralMetadataCacheReplaceResult mapResult(Long result) {
    if (Long.valueOf(1L).equals(result)) return GeneralMetadataCacheReplaceResult.APPLIED;
    if (Long.valueOf(0L).equals(result)) return GeneralMetadataCacheReplaceResult.CONFLICT;
    if (Long.valueOf(-1L).equals(result)) return GeneralMetadataCacheReplaceResult.MISSING;
    throw new CacheException("Cache replace operation failed");
  }
}
