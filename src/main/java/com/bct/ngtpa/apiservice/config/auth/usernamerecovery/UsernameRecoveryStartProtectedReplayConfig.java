package com.bct.ngtpa.apiservice.config.auth.usernamerecovery;

import com.bct.ngtpa.apiservice.adapter.out.redis.RedisCacheKeyFactory;
import com.bct.ngtpa.apiservice.adapter.out.redis.auth.usernamerecovery.replay.RedisUsernameRecoveryStartProtectedReplayAdapter;
import com.bct.ngtpa.apiservice.adapter.out.redis.auth.usernamerecovery.replay.RedisUsernameRecoveryStartProtectedReplayJsonMapper;
import com.bct.ngtpa.apiservice.adapter.out.redis.auth.usernamerecovery.replay.RedisUsernameRecoveryStartProtectedReplayKeyFactory;
import com.bct.ngtpa.apiservice.adapter.out.security.usernamerecovery.replay.AesGcmUsernameRecoveryStartReplayProtector;
import com.bct.ngtpa.apiservice.application.port.in.CreateAuthFlowSessionCsrfUseCase;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort;
import com.bct.ngtpa.apiservice.application.service.auth.usernamerecovery.context.UsernameRecoveryAuthFlowContextBinding;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency.start.SecureUsernameRecoveryStartProtectedReplayReferenceGenerator;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency.start.UsernameRecoveryStartProtectedReplayPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency.start.UsernameRecoveryStartProtectedReplayProtectorPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency.start.UsernameRecoveryStartProtectedReplayReferenceGenerator;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "redis-cache.enabled", havingValue = "true", matchIfMissing = true)
public class UsernameRecoveryStartProtectedReplayConfig {
  @Bean
  @ConditionalOnMissingBean
  UsernameRecoveryStartProtectedReplayReferenceGenerator usernameRecoveryStartReplayReferences() {
    return new SecureUsernameRecoveryStartProtectedReplayReferenceGenerator();
  }

  @Bean
  @ConditionalOnMissingBean
  UsernameRecoveryStartProtectedReplayProtectorPort usernameRecoveryStartReplayProtector(
      @Value("${shared-idempotency.username-recovery-start-replay.key-version:v1}") String version,
      @Value("${shared-idempotency.username-recovery-start-replay.secret:}") String secret) {
    return new AesGcmUsernameRecoveryStartReplayProtector(version, secret);
  }

  @Bean
  @ConditionalOnMissingBean(UsernameRecoveryStartProtectedReplayPort.class)
  UsernameRecoveryStartProtectedReplayPort usernameRecoveryStartProtectedReplayPort(
      ReactiveStringRedisTemplate redis,
      RedisCacheKeyFactory keyFactory,
      UsernameRecoveryStartProtectedReplayProtectorPort protector,
      UsernameRecoveryStartProtectedReplayReferenceGenerator references,
      AuthFlowSessionPort sessions,
      UsernameRecoveryAuthFlowContextBinding contexts,
      CreateAuthFlowSessionCsrfUseCase csrf,
      Clock clock) {
    return new RedisUsernameRecoveryStartProtectedReplayAdapter(
        redis,
        new RedisUsernameRecoveryStartProtectedReplayKeyFactory(keyFactory),
        new RedisUsernameRecoveryStartProtectedReplayJsonMapper(),
        protector,
        references,
        sessions,
        contexts,
        csrf,
        clock);
  }
}
