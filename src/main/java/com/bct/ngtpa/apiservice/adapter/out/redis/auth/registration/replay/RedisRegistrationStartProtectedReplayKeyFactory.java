package com.bct.ngtpa.apiservice.adapter.out.redis.auth.registration.replay;

import com.bct.ngtpa.apiservice.adapter.out.redis.RedisCacheKeyFactory;
import com.bct.ngtpa.apiservice.application.idempotency.ProtectedReplayReference;
import java.util.Objects;

public final class RedisRegistrationStartProtectedReplayKeyFactory {
  private final RedisCacheKeyFactory keys;

  public RedisRegistrationStartProtectedReplayKeyFactory(RedisCacheKeyFactory keys) {
    this.keys = Objects.requireNonNull(keys, "keys must not be null");
  }

  public String keyFor(ProtectedReplayReference reference) {
    Objects.requireNonNull(reference, "reference must not be null");
    return keys.key("registration", "start-protected-replay", reference.value());
  }
}
