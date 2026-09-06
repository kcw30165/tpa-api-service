package com.bct.ngtpa.apiservice.config.auth.login;
import com.bct.ngtpa.apiservice.adapter.out.json.JacksonTransientJsonAdapter;
import com.bct.ngtpa.apiservice.application.port.out.TransientJsonPort;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency.shared.LoginSharedFingerprintFactory;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency.shared.LoginSharedIdempotencyIdentityFactory;
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
}
