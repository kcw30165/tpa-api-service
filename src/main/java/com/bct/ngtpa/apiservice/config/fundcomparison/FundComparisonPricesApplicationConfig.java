package com.bct.ngtpa.apiservice.config.fundcomparison;

import com.bct.ngtpa.apiservice.application.port.out.FundComparisonPriceSourcePort;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDatePort;
import com.bct.ngtpa.apiservice.application.usecase.fundcomparison.FundComparisonPricesUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires {@link FundComparisonPricesUseCase}, mirroring {@code
 * FundComparisonOptionsApplicationConfig}'s exact confirmed single-{@code @Bean} pattern (script
 * 066 evidence): the use case itself carries no Spring annotation (application layer must not
 * depend on Spring Framework), and is constructed explicitly here from its two port dependencies.
 *
 * <p>Neither {@link FundComparisonPriceSourcePort} nor {@link ReferenceDatePort} needs an explicit
 * {@code @Bean} method here: {@code ApimFundPriceSourceAdapter} (sub-task 3.1) and {@code
 * ReferenceDateAdapter} are both already real, working beans elsewhere in the application
 * (confirmed, mirroring {@code FundPriceQueryExportUseCaseConfig}'s own documented precedent for
 * the identical {@link ReferenceDatePort} dependency). {@code FundComparisonPricesWebMapper}
 * (sub-task 3.2) is already a {@code @Component} and needs no {@code @Bean} method either.
 */
@Configuration(proxyBeanMethods = false)
public class FundComparisonPricesApplicationConfig {

  @Bean
  FundComparisonPricesUseCase fundComparisonPricesUseCase(
      FundComparisonPriceSourcePort sourcePort, ReferenceDatePort referenceDatePort) {
    return new FundComparisonPricesUseCase(sourcePort, referenceDatePort);
  }
}
