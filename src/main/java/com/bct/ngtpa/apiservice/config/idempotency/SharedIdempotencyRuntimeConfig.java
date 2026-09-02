package com.bct.ngtpa.apiservice.config.idempotency;

import com.bct.ngtpa.apiservice.adapter.out.redis.RedisCacheKeyFactory;
import com.bct.ngtpa.apiservice.adapter.out.redis.idempotency.HmacSha256IdempotencyPersistenceIdDeriver;
import com.bct.ngtpa.apiservice.adapter.out.redis.idempotency.IdempotencyPersistenceIdDeriver;
import com.bct.ngtpa.apiservice.adapter.out.redis.idempotency.IdempotencyPersistenceSecretFactory;
import com.bct.ngtpa.apiservice.adapter.out.redis.idempotency.ReactiveRedisIdempotencyOperations;
import com.bct.ngtpa.apiservice.adapter.out.redis.idempotency.RedisIdempotencyAdapter;
import com.bct.ngtpa.apiservice.adapter.out.redis.idempotency.RedisIdempotencyJsonMapper;
import com.bct.ngtpa.apiservice.adapter.out.redis.idempotency.RedisIdempotencyKeyFactory;
import com.bct.ngtpa.apiservice.adapter.out.redis.idempotency.RedisIdempotencyOperations;
import com.bct.ngtpa.apiservice.application.idempotency.DefaultIdempotencyExecutor;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyExecutor;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyPort;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyReservationTokenGenerator;
import com.bct.ngtpa.apiservice.application.idempotency.SecureIdempotencyReservationTokenGenerator;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

/** Runtime-only composition for the shared idempotency capability. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
    prefix = "shared-idempotency",
    name = "enabled",
    havingValue = "true")
public class SharedIdempotencyRuntimeConfig {
  @Bean
  @ConditionalOnMissingBean(IdempotencyPersistenceIdDeriver.class)
  public IdempotencyPersistenceIdDeriver idempotencyPersistenceIdDeriver(
      @Value("${shared-idempotency.persistence-hmac-secret:}") String configuredSecret) {
    return new HmacSha256IdempotencyPersistenceIdDeriver(
        IdempotencyPersistenceSecretFactory.decode(configuredSecret));
  }

  @Bean
  @ConditionalOnMissingBean
  public RedisIdempotencyKeyFactory redisIdempotencyKeyFactory(
      RedisCacheKeyFactory keys, IdempotencyPersistenceIdDeriver persistenceIds) {
    return new RedisIdempotencyKeyFactory(keys, persistenceIds);
  }

  @Bean
  @ConditionalOnMissingBean
  public RedisIdempotencyJsonMapper redisIdempotencyJsonMapper() {
    return new RedisIdempotencyJsonMapper();
  }

  @Bean
  @ConditionalOnMissingBean(RedisIdempotencyOperations.class)
  public RedisIdempotencyOperations redisIdempotencyOperations(ReactiveStringRedisTemplate redis) {
    return new ReactiveRedisIdempotencyOperations(redis);
  }

  @Bean
  @ConditionalOnMissingBean(IdempotencyPort.class)
  public IdempotencyPort idempotencyPort(
      RedisIdempotencyOperations redis,
      RedisIdempotencyKeyFactory keys,
      RedisIdempotencyJsonMapper json) {
    return new RedisIdempotencyAdapter(redis, keys, json);
  }

  @Bean
  @ConditionalOnMissingBean
  public IdempotencyReservationTokenGenerator idempotencyReservationTokenGenerator() {
    return new SecureIdempotencyReservationTokenGenerator();
  }

  @Bean
  @ConditionalOnMissingBean(IdempotencyExecutor.class)
  public IdempotencyExecutor idempotencyExecutor(
      IdempotencyPort persistence, IdempotencyReservationTokenGenerator tokens, Clock clock) {
    return new DefaultIdempotencyExecutor(persistence, tokens, clock);
  }
}
