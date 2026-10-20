package com.bct.ngtpa.apiservice.config.fundcomparison;

import com.bct.ngtpa.apiservice.application.port.out.AuthenticatedSessionPort;
import com.bct.ngtpa.apiservice.application.port.out.AuthenticatedSessionProtectorPort;
import com.bct.ngtpa.apiservice.application.port.out.FundComparisonOptionsSourcePort;
import com.bct.ngtpa.apiservice.application.port.out.TransientJsonPort;
import com.bct.ngtpa.apiservice.application.usecase.fundcomparison.FundComparisonOptionsCacheService;
import com.bct.ngtpa.apiservice.application.usecase.fundcomparison.FundComparisonOptionsUseCase;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.AuthenticatedSessionConcurrencyService;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires {@link FundComparisonOptionsUseCase} and the session options cache it writes to (sub-task
 * 5.3). Neither class carries a Spring annotation (application layer stays framework-free).
 *
 * <p>Every dependency already exists as a bean: {@code AuthenticatedSessionConfig} (session port,
 * protector), {@code AuthenticatedSessionConcurrencyConfig} (concurrency service), the shared
 * {@link TransientJsonPort} ({@code @ConditionalOnMissingBean} in the login and username-recovery
 * configs), {@code ApplicationClockConfig} ({@link Clock}), and {@code
 * ApimFundComparisonOptionsSourceAdapter} ({@link FundComparisonOptionsSourcePort}, a
 * {@code @Component}).
 */
@Configuration(proxyBeanMethods = false)
public class FundComparisonOptionsApplicationConfig {

  @Bean
  FundComparisonOptionsCacheService fundComparisonOptionsCacheService(
      AuthenticatedSessionPort sessions,
      AuthenticatedSessionProtectorPort protector,
      AuthenticatedSessionConcurrencyService concurrency,
      TransientJsonPort json,
      Clock clock) {
    return new FundComparisonOptionsCacheService(sessions, protector, concurrency, json, clock);
  }

  @Bean
  FundComparisonOptionsUseCase fundComparisonOptionsUseCase(
      FundComparisonOptionsSourcePort sourcePort, FundComparisonOptionsCacheService cacheService) {
    return new FundComparisonOptionsUseCase(sourcePort, cacheService);
  }
}
