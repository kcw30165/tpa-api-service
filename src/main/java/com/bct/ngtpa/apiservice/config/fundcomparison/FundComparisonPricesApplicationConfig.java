package com.bct.ngtpa.apiservice.config.fundcomparison;

import com.bct.ngtpa.apiservice.application.port.out.FundComparisonPriceSourcePort;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDatePort;
import com.bct.ngtpa.apiservice.application.usecase.fundcomparison.FundComparisonOptionsCacheService;
import com.bct.ngtpa.apiservice.application.usecase.fundcomparison.FundComparisonOptionsUseCase;
import com.bct.ngtpa.apiservice.application.usecase.fundcomparison.FundComparisonPricesUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires {@link FundComparisonPricesUseCase} (membership validation against the session-cached
 * options, then fetch and assemble). The use case carries no Spring annotation (application layer
 * stays framework-free).
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
      FundComparisonPriceSourcePort sourcePort,
      ReferenceDatePort referenceDatePort,
      FundComparisonOptionsUseCase optionsUseCase,
      FundComparisonOptionsCacheService cacheService) {
    return new FundComparisonPricesUseCase(
        sourcePort, referenceDatePort, optionsUseCase, cacheService);
  }
}
