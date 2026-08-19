package com.bct.ngtpa.apiservice.adapter.out.redis.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.out.redis.RedisCacheKeyFactory;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import org.junit.jupiter.api.Test;

class RedisAuthFlowSessionKeyFactoryTest {

  @Test
  void buildsAuthFlowSessionKeyWithAuthFlowSessionNamespace() {
    var keyFactory = new RedisCacheKeyFactory("ngtpa");
    var key =
        RedisAuthFlowSessionKeyFactory.keyFor(keyFactory, new AuthFlowSessionId("afs_abc123"));

    assertThat(key).isEqualTo("ngtpa:auth:flow-session:afs_abc123");
    assertThat(key)
        .doesNotContain("auth_flow_sid")
        .doesNotContain("email")
        .doesNotContain("mobile")
        .doesNotContain("csrf")
        .doesNotContain("XSRF-TOKEN")
        .doesNotContain("member@example.com")
        .doesNotContain("91234567");
  }
}
