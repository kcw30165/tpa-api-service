package com.bct.ngtpa.apiservice.config.fundcomparison;

import com.bct.ngtpa.apiservice.application.port.out.FundComparisonPriceSourcePort;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDatePort;
import com.bct.ngtpa.apiservice.application.usecase.fundcomparison.FundComparisonOptionsCacheService;
import com.bct.ngtpa.apiservice.application.usecase.fundcomparison.FundComparisonOptionsUseCase;
import com.bct.ngtpa.apiservice.application.usecase.fundcomparison.FundComparisonPricesUseCase;
import com.bct.ngtpa.apiservice.application.usecase.fundcomparison.FundComparisonSelectedPricesUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires {@link FundComparisonPricesUseCase} (fetch and assemble) and {@link
 * FundComparisonSelectedPricesUseCase} (membership validation against the session-cached options,
 * sub-task 5.4). Neither class carries a Spring annotation (application layer stays
 * framework-free).
 *
 * <p>{@link FundComparisonPriceSourcePort} ({@code ApimFundPriceSourceAdapter}) and {@link
 * ReferenceDatePort} ({@code ReferenceDateAdapter}) are existing beans; {@link
 * FundComparisonOptionsUseCase} and {@link FundComparisonOptionsCacheService} come from {@code
 * FundComparisonOptionsApplicationConfig}.
 */
@Configuration(proxyBeanMethods = false)
public class FundComparisonPricesApplicationConfig {

  @Bean
  FundComparisonPricesUseCase fundComparisonPricesUseCase(
      FundComparisonPriceSourcePort sourcePort, ReferenceDatePort referenceDatePort) {
    return new FundComparisonPricesUseCase(sourcePort, referenceDatePort);
  }

  @Bean
  FundComparisonSelectedPricesUseCase fundComparisonSelectedPricesUseCase(
      FundComparisonOptionsUseCase optionsUseCase,
      FundComparisonOptionsCacheService cacheService,
      FundComparisonPricesUseCase pricesUseCase) {
    return new FundComparisonSelectedPricesUseCase(optionsUseCase, cacheService, pricesUseCase);
  }
}
