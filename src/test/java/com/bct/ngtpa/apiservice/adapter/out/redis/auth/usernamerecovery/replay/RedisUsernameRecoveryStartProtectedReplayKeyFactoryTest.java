package com.bct.ngtpa.apiservice.adapter.out.redis.auth.usernamerecovery.replay;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.out.redis.RedisCacheKeyFactory;
import com.bct.ngtpa.apiservice.application.idempotency.ProtectedReplayReference;
import org.junit.jupiter.api.Test;

class RedisUsernameRecoveryStartProtectedReplayKeyFactoryTest {
  @Test
  void keyIsCapabilityScopedOpaqueAndSeparateFromTerminalSnapshots() {
    String key =
        new RedisUsernameRecoveryStartProtectedReplayKeyFactory(new RedisCacheKeyFactory("ngtpa"))
            .keyFor(new ProtectedReplayReference("usernameRecoveryStartReplayReference001"));
    assertThat(key)
        .isEqualTo(
            "ngtpa:username-recovery:start-protected-replay:usernameRecoveryStartReplayReference001");
    assertThat(key).doesNotContain(":idempotency:", "auth_flow_sid", "csrf", "member@example.com");
  }
}
