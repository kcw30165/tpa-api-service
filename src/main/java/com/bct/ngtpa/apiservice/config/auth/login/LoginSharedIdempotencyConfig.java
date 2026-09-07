package com.bct.ngtpa.apiservice.config.auth.login;

import com.bct.ngtpa.apiservice.adapter.out.json.JacksonTransientJsonAdapter;
import com.bct.ngtpa.apiservice.adapter.out.redis.auth.login.RedisLoginIdempotencyKeyFactory;
import com.bct.ngtpa.apiservice.application.port.out.TransientJsonPort;
import com.bct.ngtpa.apiservice.application.service.auth.login.idempotency.LoginIdempotencyPersistenceIdDeriver;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency.DefaultLoginOtpVerifyProtectedReplayResolver;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency.LoginFingerprintFactory;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency.LoginIdempotencyIdentityFactory;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency.LoginOtpVerifyExecutionBridge;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency.LoginOtpVerifyProtectedReplayResolver;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency.LoginOtpVerifyReplayCodec;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency.LoginResponseEnvelope;
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
  LoginIdempotencyIdentityFactory loginSharedIdempotencyIdentityFactory() {
    return new LoginIdempotencyIdentityFactory();
  }

  @Bean
  LoginFingerprintFactory loginSharedFingerprintFactory() {
    return new LoginFingerprintFactory();
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
    return new LoginOtpVerifyReplayCodec(new LoginResponseEnvelope(json), protectedReplayResolver);
  }

  @Bean
  LoginOtpVerifyExecutionBridge loginOtpVerifySharedExecutionBridge(
      TransientJsonPort json, LoginOtpVerifyReplayCodec replayCodec) {
    return new LoginOtpVerifyExecutionBridge(json, replayCodec);
  }
}
