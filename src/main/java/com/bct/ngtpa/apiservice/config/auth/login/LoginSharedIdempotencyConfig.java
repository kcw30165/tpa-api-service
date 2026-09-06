package com.bct.ngtpa.apiservice.config.auth.login;

import com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency.shared.LoginSharedFingerprintFactory;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency.shared.LoginSharedIdempotencyIdentityFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Shared outer composition for Login idempotency identity and request fingerprint factories. */
@Configuration(proxyBeanMethods = false)
public class LoginSharedIdempotencyConfig {
  @Bean
  LoginSharedIdempotencyIdentityFactory loginSharedIdempotencyIdentityFactory() {
    return new LoginSharedIdempotencyIdentityFactory();
  }

  @Bean
  LoginSharedFingerprintFactory loginSharedFingerprintFactory() {
    return new LoginSharedFingerprintFactory();
  }
}
