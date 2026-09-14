package com.bct.ngtpa.apiservice.adapter.out.redis.auth.forgotpassword.replay;

import com.bct.ngtpa.apiservice.adapter.out.redis.RedisCacheKeyFactory;
import com.bct.ngtpa.apiservice.application.idempotency.ProtectedReplayReference;
import java.util.Objects;

public final class RedisForgotPasswordStartProtectedReplayKeyFactory {
  private final RedisCacheKeyFactory keys;

  public RedisForgotPasswordStartProtectedReplayKeyFactory(RedisCacheKeyFactory keys) {
    this.keys = Objects.requireNonNull(keys, "keys must not be null");
  }

  public String keyFor(ProtectedReplayReference reference) {
    Objects.requireNonNull(reference, "reference must not be null");
    return keys.key("forgot-password", "start-protected-replay", reference.value());
  }
}
