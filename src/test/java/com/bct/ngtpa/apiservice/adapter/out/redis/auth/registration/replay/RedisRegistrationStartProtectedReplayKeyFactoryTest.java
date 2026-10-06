package com.bct.ngtpa.apiservice.adapter.out.redis.auth.registration.replay;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.out.redis.RedisCacheKeyFactory;
import com.bct.ngtpa.apiservice.application.idempotency.ProtectedReplayReference;
import org.junit.jupiter.api.Test;

class RedisRegistrationStartProtectedReplayKeyFactoryTest {

  @Test
  void keyIsCapabilityScopedOpaqueAndSeparateFromTerminalSnapshots() {
    String key =
        new RedisRegistrationStartProtectedReplayKeyFactory(new RedisCacheKeyFactory("ngtpa"))
            .keyFor(new ProtectedReplayReference("registrationStartReplayReference001"));
    assertThat(key)
        .isEqualTo("ngtpa:registration:start-protected-replay:registrationStartReplayReference001");
    assertThat(key).doesNotContain(":idempotency:", "auth_flow_sid", "csrf", "member@example.com");
  }
}
