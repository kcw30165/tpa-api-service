package com.bct.ngtpa.apiservice.config.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.out.redis.RedisCacheKeyFactory;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyExecutor;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyPort;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

class SharedIdempotencyRuntimeConfigTest {
  private final ApplicationContextRunner context =
      new ApplicationContextRunner()
          .withUserConfiguration(SharedIdempotencyRuntimeConfig.class)
          .withBean(RedisCacheKeyFactory.class, () -> new RedisCacheKeyFactory("ngtpa"))
          .withBean(Clock.class, Clock::systemUTC)
          .withBean(
              ReactiveStringRedisTemplate.class,
              () -> org.mockito.Mockito.mock(ReactiveStringRedisTemplate.class));

  @Test
  void missingSecretFailsClosedBecauseSharedRuntimeIsMandatory() {
    context
        .withPropertyValues("shared-idempotency.persistence-hmac-secret=")
        .run(
        result -> {
          assertThat(result).hasFailed();
          assertThat(result.getStartupFailure())
              .hasMessageNotContaining("SHARED_IDEMPOTENCY_PERSISTENCE_HMAC_SECRET");
        });
  }

  @Test
  void validSecretComposesSingleSharedPortAndDefaultExecutor() {
    String secret =
        Base64.getEncoder()
            .encodeToString(
                "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));
    context
        .withPropertyValues("shared-idempotency.persistence-hmac-secret=" + secret)
        .run(
            result -> {
              assertThat(result).hasNotFailed();
              assertThat(result).hasSingleBean(IdempotencyPort.class);
              assertThat(result).hasSingleBean(IdempotencyExecutor.class);
              assertThat(result.getBean(IdempotencyExecutor.class))
                  .isInstanceOf(
                      com.bct.ngtpa.apiservice.application.idempotency.DefaultIdempotencyExecutor.class);
            });
  }

}
