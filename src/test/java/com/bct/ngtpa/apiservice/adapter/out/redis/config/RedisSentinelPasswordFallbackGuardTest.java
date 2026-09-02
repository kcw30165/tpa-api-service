package com.bct.ngtpa.apiservice.adapter.out.redis.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class RedisSentinelPasswordFallbackGuardTest {

  @Test
  void sharedConfigurationDoesNotEmbedSentinelPasswordFallback() throws IOException {
    String yaml =
        new ClassPathResource("application.yml")
            .getContentAsString(StandardCharsets.UTF_8);

    assertThat(yaml)
        .contains("${REDIS_SENTINEL_PASSWORD:}")
        .doesNotContainPattern("\\$\\{REDIS_SENTINEL_PASSWORD:[^}]+}")
        .contains("${REDIS_PASSWORD:}")
        .contains("${REDIS_SENTINEL_MASTER:")
        .contains("${REDIS_SENTINEL_NODES:");
  }
}
