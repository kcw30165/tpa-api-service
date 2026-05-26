package com.bct.ngtpa.apiservice.adapter.out.redis.config;

import com.bct.ngtpa.apiservice.application.port.out.CachePort;
import com.bct.ngtpa.apiservice.adapter.out.redis.RedisCacheKeyFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the Redis adapter respects the {@code redis-cache.enabled} conditional.
 *
 * <p>When {@code redis-cache.enabled=false}, no Redis beans should be registered and the
 * application must start without a live Redis connection — this is required for local
 * development without a Sentinel cluster.
 */
class RedisAdapterConfigConditionalTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(RedisAdapterConfig.class);

    @Test
    void redisBeansAreAbsentWhenRedisCacheDisabled() {
        contextRunner
                .withPropertyValues("redis-cache.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(LettuceConnectionFactory.class);
                    assertThat(context).doesNotHaveBean(ReactiveRedisTemplate.class);
                    assertThat(context).doesNotHaveBean(RedisCacheKeyFactory.class);
                    assertThat(context).doesNotHaveBean(CachePort.class);
                });
    }

    @Test
    void applicationContextStartsWithoutErrorsWhenRedisCacheDisabled() {
        contextRunner
                .withPropertyValues("redis-cache.enabled=false")
                .run(context -> assertThat(context).hasNotFailed());
    }
}
