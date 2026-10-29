package com.bct.ngtpa.apiservice.config.auth.passwordvalidation;

import com.bct.ngtpa.apiservice.application.port.out.RateLimitPort;
import com.bct.ngtpa.apiservice.application.usecase.passwordvalidation.PasswordValidationGrantService;
import com.bct.ngtpa.apiservice.application.usecase.passwordvalidation.PasswordValidationThrottleService;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.AuthenticatedSessionConcurrencyService;
import com.bct.ngtpa.apiservice.infrastructure.security.passwordvalidation.HmacPasswordValidationBucketIdDeriver;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PasswordValidationConfig {
  @Bean
  PasswordValidationGrantService passwordValidationGrantService(
      AuthenticatedSessionConcurrencyService concurrency,
      @Value("${password-validation.grant.ttl-seconds:300}") int ttlSeconds) {
    return new PasswordValidationGrantService(concurrency, ttlSeconds);
  }

  @Bean
  PasswordValidationThrottleService passwordValidationThrottleService(
      RateLimitPort limiter,
      @Value("${password-validation.rate-limit.secret:}") String secret,
      @Value("${password-validation.rate-limit.max-attempts:10}") int limit,
      @Value("${password-validation.rate-limit.window-seconds:60}") int windowSeconds) {
    byte[] key = new byte[0];
    if (!secret.isEmpty()) {
      try {
        key = Base64.getDecoder().decode(secret);
      } catch (IllegalArgumentException exception) {
        throw new IllegalArgumentException(
            "Invalid password-validation limiter secret configuration");
      }
      if (key.length < 32) {
        throw new IllegalArgumentException("Password-validation limiter secret is too short");
      }
    }
    return new PasswordValidationThrottleService(
        limiter, new HmacPasswordValidationBucketIdDeriver(key), limit, windowSeconds);
  }
}
