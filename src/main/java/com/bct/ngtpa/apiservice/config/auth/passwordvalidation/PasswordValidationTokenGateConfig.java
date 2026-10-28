package com.bct.ngtpa.apiservice.config.auth.passwordvalidation;

import com.bct.ngtpa.apiservice.adapter.out.passwordvalidation.JsonOperationPayloadCanonicalizer;
import com.bct.ngtpa.apiservice.application.port.out.OperationPayloadBindingPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PasswordValidationTokenGateConfig {
  @Bean
  OperationPayloadBindingPort passwordValidationMutationBindings() {
    return new JsonOperationPayloadCanonicalizer();
  }
}
