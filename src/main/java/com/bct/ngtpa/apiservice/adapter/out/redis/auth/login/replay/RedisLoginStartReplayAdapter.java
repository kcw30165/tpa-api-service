package com.bct.ngtpa.apiservice.adapter.out.redis.auth.login.replay;

import com.bct.ngtpa.apiservice.application.dto.auth.login.replay.*;
import com.bct.ngtpa.apiservice.application.port.out.LoginStartReplayPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Optional;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;

public final class RedisLoginStartReplayAdapter implements LoginStartReplayPort {
  private final RedisLoginStartReplayKeyFactory keys;
  private final ReactiveStringRedisTemplate redis;
  private final ObjectMapper mapper;

  public RedisLoginStartReplayAdapter(
      RedisLoginStartReplayKeyFactory keys,
      ReactiveStringRedisTemplate redis,
      ObjectMapper mapper) {
    this.keys = keys;
    this.redis = redis;
    this.mapper = mapper;
  }

  public Mono<Void> save(
      LoginStartReplayCommitRef ref, ProtectedLoginStartReplay replay, Duration ttl) {
    if (ttl == null || ttl.isZero() || ttl.isNegative())
      return Mono.error(new IllegalArgumentException("ttl must be positive"));
    return redis.opsForValue().set(keys.keyFor(ref), json(replay), ttl).then();
  }

  public Mono<Optional<ProtectedLoginStartReplay>> load(LoginStartReplayCommitRef ref) {
    return redis
        .opsForValue()
        .get(keys.keyFor(ref))
        .map(v -> Optional.of(read(v)))
        .defaultIfEmpty(Optional.empty());
  }

  public Mono<Boolean> delete(LoginStartReplayCommitRef ref) {
    return redis.delete(keys.keyFor(ref)).map(v -> v > 0);
  }

  private String json(ProtectedLoginStartReplay v) {
    try {
      return mapper.writeValueAsString(v);
    } catch (Exception e) {
      throw new IllegalStateException("Unable to serialize protected login replay");
    }
  }

  private ProtectedLoginStartReplay read(String v) {
    try {
      return mapper.readValue(v, ProtectedLoginStartReplay.class);
    } catch (Exception e) {
      throw new IllegalStateException("Unable to deserialize protected login replay");
    }
  }
}
