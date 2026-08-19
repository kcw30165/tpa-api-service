package com.bct.ngtpa.apiservice.config.session;

import com.bct.ngtpa.apiservice.application.usecase.session.authflow.AuthFlowSessionConcurrencyService;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.AuthFlowSessionLifecycleService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Shared runtime wiring for auth-flow session concurrency across authentication workflows. */
@Configuration
public class AuthFlowSessionConcurrencyConfig {
  @Bean
  @ConditionalOnMissingBean(AuthFlowSessionConcurrencyService.class)
  AuthFlowSessionConcurrencyService authFlowSessionConcurrencyService(
      AuthFlowSessionLifecycleService lifecycleService) {
    return new AuthFlowSessionConcurrencyService(lifecycleService);
  }
}
