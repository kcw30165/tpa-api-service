package com.bct.ngtpa.apiservice.config.registration;

import com.bct.ngtpa.apiservice.adapter.out.redis.RedisCacheKeyFactory;
import com.bct.ngtpa.apiservice.adapter.out.redis.auth.registration.replay.RedisRegistrationStartProtectedReplayAdapter;
import com.bct.ngtpa.apiservice.adapter.out.redis.auth.registration.replay.RedisRegistrationStartProtectedReplayJsonMapper;
import com.bct.ngtpa.apiservice.adapter.out.redis.auth.registration.replay.RedisRegistrationStartProtectedReplayKeyFactory;
import com.bct.ngtpa.apiservice.adapter.out.security.registration.replay.AesGcmRegistrationStartReplayProtector;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency.RegistrationStartProtectedReplayPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency.RegistrationStartProtectedReplayProtectorPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency.RegistrationStartProtectedReplayReferenceGenerator;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency.SecureRegistrationStartProtectedReplayReferenceGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "shared-idempotency", name = "enabled", havingValue = "true")
public class RegistrationStartProtectedReplayConfig {
  @Bean
  @ConditionalOnMissingBean
  RegistrationStartProtectedReplayReferenceGenerator registrationStartReplayReferences() {
    return new SecureRegistrationStartProtectedReplayReferenceGenerator();
  }

  @Bean
  @ConditionalOnMissingBean
  RegistrationStartProtectedReplayProtectorPort registrationStartReplayProtector(
      @Value("${shared-idempotency.registration-start-replay.key-version:v1}") String version,
      @Value("${shared-idempotency.registration-start-replay.secret:}") String secret) {
    return new AesGcmRegistrationStartReplayProtector(version, secret);
  }

  @Bean
  @ConditionalOnMissingBean(RegistrationStartProtectedReplayPort.class)
  RegistrationStartProtectedReplayPort registrationStartProtectedReplayPort(
      ReactiveStringRedisTemplate redis,
      RedisCacheKeyFactory keyFactory,
      RegistrationStartProtectedReplayProtectorPort protector,
      RegistrationStartProtectedReplayReferenceGenerator references) {
    return new RedisRegistrationStartProtectedReplayAdapter(
        redis,
        new RedisRegistrationStartProtectedReplayKeyFactory(keyFactory),
        new RedisRegistrationStartProtectedReplayJsonMapper(),
        protector,
        references);
  }
}
