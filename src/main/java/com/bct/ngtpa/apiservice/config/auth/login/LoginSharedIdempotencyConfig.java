package com.bct.ngtpa.apiservice.config.auth.login;

import com.bct.ngtpa.apiservice.adapter.out.json.JacksonTransientJsonAdapter;
import com.bct.ngtpa.apiservice.adapter.out.redis.auth.login.RedisLoginIdempotencyKeyFactory;
import com.bct.ngtpa.apiservice.application.port.out.TransientJsonPort;
import com.bct.ngtpa.apiservice.application.service.auth.login.idempotency.LoginIdempotencyPersistenceIdDeriver;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency.shared.DefaultLoginOtpVerifyProtectedReplayResolver;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency.shared.LoginOtpVerifyProtectedReplayResolver;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency.shared.LoginOtpVerifyReplayCodec;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency.shared.LoginOtpVerifySharedExecutionBridge;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency.shared.LoginSharedFingerprintFactory;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency.shared.LoginSharedIdempotencyIdentityFactory;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency.shared.LoginSharedResponseEnvelope;
import com.bct.ngtpa.apiservice.infrastructure.security.idempotency.HmacLoginIdempotencyPersistenceIdDeriver;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Base64;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Shared outer composition for Login idempotency identity and request fingerprint factories. */
@Configuration(proxyBeanMethods = false)
public class LoginSharedIdempotencyConfig {
  @Bean
  TransientJsonPort loginTransientJsonPort(ObjectMapper mapper) {
    return new JacksonTransientJsonAdapter(mapper);
  }

  @Bean
  LoginSharedIdempotencyIdentityFactory loginSharedIdempotencyIdentityFactory() {
    return new LoginSharedIdempotencyIdentityFactory();
  }

  @Bean
  LoginSharedFingerprintFactory loginSharedFingerprintFactory() {
    return new LoginSharedFingerprintFactory();
  }

  @Bean
  RedisLoginIdempotencyKeyFactory redisLoginIdempotencyKeyFactory(
      @org.springframework.beans.factory.annotation.Value("${redis-cache.key-prefix:ngtpa}")
          String keyPrefix) {
    return new RedisLoginIdempotencyKeyFactory(keyPrefix);
  }

  @Bean
  LoginIdempotencyPersistenceIdDeriver loginIdempotencyPersistenceIdDeriver(
      @org.springframework.beans.factory.annotation.Value(
              "${auth-workflows.login.idempotency.persistence-hmac-secret:}")
          String configuredSecret) {
    try {
      return new HmacLoginIdempotencyPersistenceIdDeriver(
          Base64.getDecoder().decode(configuredSecret));
    } catch (IllegalArgumentException exception) {
      throw new IllegalStateException("Login OTP Verify persistence HMAC secret is invalid");
    }
  }

  @Bean
  LoginOtpVerifyProtectedReplayResolver loginOtpVerifyProtectedReplayResolver(
      AutowireCapableBeanFactory beanFactory) {
    return beanFactory.createBean(DefaultLoginOtpVerifyProtectedReplayResolver.class);
  }

  @Bean
  LoginOtpVerifyReplayCodec loginOtpVerifyReplayCodec(
      TransientJsonPort json, LoginOtpVerifyProtectedReplayResolver protectedReplayResolver) {
    return new LoginOtpVerifyReplayCodec(
        new LoginSharedResponseEnvelope(json), protectedReplayResolver);
  }

  @Bean
  LoginOtpVerifySharedExecutionBridge loginOtpVerifySharedExecutionBridge(
      TransientJsonPort json, LoginOtpVerifyReplayCodec replayCodec) {
    return new LoginOtpVerifySharedExecutionBridge(json, replayCodec);
  }
}
