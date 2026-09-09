package com.bct.ngtpa.apiservice.adapter.out.redis.auth.usernamerecovery.replay;

import com.bct.ngtpa.apiservice.adapter.out.redis.RedisCacheKeyFactory;
import com.bct.ngtpa.apiservice.application.idempotency.ProtectedReplayReference;
import java.util.Objects;

public final class RedisUsernameRecoveryStartProtectedReplayKeyFactory {
  private final RedisCacheKeyFactory keys;

  public RedisUsernameRecoveryStartProtectedReplayKeyFactory(RedisCacheKeyFactory keys) {
    this.keys = Objects.requireNonNull(keys, "keys must not be null");
  }

  public String keyFor(ProtectedReplayReference reference) {
    Objects.requireNonNull(reference, "reference must not be null");
    return keys.key("username-recovery", "start-protected-replay", reference.value());
  }
}
