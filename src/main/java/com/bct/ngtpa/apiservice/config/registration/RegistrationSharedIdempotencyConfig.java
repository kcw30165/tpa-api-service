package com.bct.ngtpa.apiservice.config.registration;

import com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency.RegistrationSharedFingerprintFactory;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency.RegistrationSharedIdempotencyIdentityFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Outer-layer composition for Registration-owned shared idempotency factories. */
@Configuration
public class RegistrationSharedIdempotencyConfig {

  @Bean
  @ConditionalOnMissingBean(RegistrationSharedIdempotencyIdentityFactory.class)
  RegistrationSharedIdempotencyIdentityFactory registrationSharedIdempotencyIdentityFactory() {
    return new RegistrationSharedIdempotencyIdentityFactory();
  }

  @Bean
  @ConditionalOnMissingBean(RegistrationSharedFingerprintFactory.class)
  RegistrationSharedFingerprintFactory registrationSharedFingerprintFactory() {
    return new RegistrationSharedFingerprintFactory();
  }
}
