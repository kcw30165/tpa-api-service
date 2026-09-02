package com.bct.ngtpa.apiservice.config.registration;

import com.bct.ngtpa.apiservice.adapter.out.registration.RegistrationEligibleTrustSessionAdapter;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationEligibleTrustPersistencePort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RegistrationEligibleTrustConfig {
  @Bean
  @ConditionalOnMissingBean(RegistrationEligibleTrustPersistencePort.class)
  RegistrationEligibleTrustPersistencePort registrationEligibleTrustPersistencePort(
      AuthFlowSessionPort sessions) {
    return new RegistrationEligibleTrustSessionAdapter(sessions);
  }
}
