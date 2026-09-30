package com.bct.ngtpa.apiservice.adapter.out.redis.auth.forgotpassword.replay;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.out.redis.RedisCacheKeyFactory;
import com.bct.ngtpa.apiservice.application.idempotency.ProtectedReplayReference;
import org.junit.jupiter.api.Test;

class RedisForgotPasswordStartProtectedReplayKeyFactoryTest {

  @Test
  void keyIsCapabilityScopedOpaqueAndSeparateFromTerminalSnapshots() {
    String key =
        new RedisForgotPasswordStartProtectedReplayKeyFactory(new RedisCacheKeyFactory("ngtpa"))
            .keyFor(new ProtectedReplayReference("forgotPasswordStartReplayReference001"));
    assertThat(key)
        .isEqualTo(
            "ngtpa:forgot-password:start-protected-replay:forgotPasswordStartReplayReference001");
    assertThat(key).doesNotContain(":idempotency:", "auth_flow_sid", "csrf", "member@example.com");
  }
}
