package com.bct.ngtpa.apiservice.config.auth.forgotpassword;

import com.bct.ngtpa.apiservice.adapter.out.redis.RedisCacheKeyFactory;
import com.bct.ngtpa.apiservice.adapter.out.redis.auth.forgotpassword.replay.RedisForgotPasswordStartProtectedReplayAdapter;
import com.bct.ngtpa.apiservice.adapter.out.redis.auth.forgotpassword.replay.RedisForgotPasswordStartProtectedReplayJsonMapper;
import com.bct.ngtpa.apiservice.adapter.out.redis.auth.forgotpassword.replay.RedisForgotPasswordStartProtectedReplayKeyFactory;
import com.bct.ngtpa.apiservice.adapter.out.security.forgotpassword.replay.AesGcmForgotPasswordStartReplayProtector;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.shared.ForgotPasswordStartProtectedReplayPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.shared.ForgotPasswordStartProtectedReplayProtectorPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.shared.ForgotPasswordStartProtectedReplayReferenceGenerator;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.shared.SecureForgotPasswordStartProtectedReplayReferenceGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

@Configuration(proxyBeanMethods = false)
public class ForgotPasswordStartProtectedReplayConfig {
  @Bean
  @ConditionalOnMissingBean
  ForgotPasswordStartProtectedReplayReferenceGenerator forgotPasswordStartReplayReferences() {
    return new SecureForgotPasswordStartProtectedReplayReferenceGenerator();
  }

  @Bean
  @ConditionalOnMissingBean
  ForgotPasswordStartProtectedReplayProtectorPort forgotPasswordStartReplayProtector(
      @Value("${shared-idempotency.forgot-password-start-replay.key-version:v1}") String version,
      @Value("${shared-idempotency.forgot-password-start-replay.secret:}") String secret) {
    return new AesGcmForgotPasswordStartReplayProtector(version, secret);
  }

  @Bean
  @ConditionalOnMissingBean(ForgotPasswordStartProtectedReplayPort.class)
  ForgotPasswordStartProtectedReplayPort forgotPasswordStartProtectedReplayPort(
      ReactiveStringRedisTemplate redis,
      RedisCacheKeyFactory keyFactory,
      ForgotPasswordStartProtectedReplayProtectorPort protector,
      ForgotPasswordStartProtectedReplayReferenceGenerator references) {
    return new RedisForgotPasswordStartProtectedReplayAdapter(
        redis,
        new RedisForgotPasswordStartProtectedReplayKeyFactory(keyFactory),
        new RedisForgotPasswordStartProtectedReplayJsonMapper(),
        protector,
        references);
  }
}
