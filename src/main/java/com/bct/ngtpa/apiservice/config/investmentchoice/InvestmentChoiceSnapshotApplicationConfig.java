package com.bct.ngtpa.apiservice.config.investmentchoice;

import com.bct.ngtpa.apiservice.application.port.out.AuthenticatedSessionPort;
import com.bct.ngtpa.apiservice.application.port.out.AuthenticatedSessionProtectorPort;
import com.bct.ngtpa.apiservice.application.port.out.TransientJsonPort;
import com.bct.ngtpa.apiservice.application.usecase.investmentchoice.InvestmentChoiceSnapshotCacheService;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.AuthenticatedSessionConcurrencyService;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the investment-choice session snapshot cache without adding framework dependencies to it.
 */
@Configuration(proxyBeanMethods = false)
public class InvestmentChoiceSnapshotApplicationConfig {

  @Bean
  InvestmentChoiceSnapshotCacheService investmentChoiceSnapshotCacheService(
      AuthenticatedSessionPort sessions,
      AuthenticatedSessionProtectorPort protector,
      AuthenticatedSessionConcurrencyService concurrency,
      TransientJsonPort json,
      Clock clock) {
    return new InvestmentChoiceSnapshotCacheService(sessions, protector, concurrency, json, clock);
  }
}
