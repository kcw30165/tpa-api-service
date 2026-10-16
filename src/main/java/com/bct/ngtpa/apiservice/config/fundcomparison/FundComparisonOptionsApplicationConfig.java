package com.bct.ngtpa.apiservice.config.fundcomparison;

import com.bct.ngtpa.apiservice.application.port.out.FundComparisonOptionsSourcePort;
import com.bct.ngtpa.apiservice.application.usecase.fundcomparison.FundComparisonOptionsUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires {@link FundComparisonOptionsUseCase}, mirroring {@code FundFactSheetApplicationConfig}'s
 * exact confirmed pattern (script 042 evidence): the use case itself carries no Spring annotation
 * (application layer must not depend on Spring Framework), and is constructed explicitly here from
 * its dependencies.
 *
 * <p>Corrected (script 047): the use case constructor no longer takes {@code
 * FundComparisonOptionsMapper} (moved to the controller, closing a Clean Architecture violation);
 * this {@code @Bean} factory method signature now matches.
 *
 * <p>{@link FundComparisonOptionsSourcePort} needs no explicit {@code @Bean} method: {@code
 * ApimFundComparisonOptionsSourceAdapter} is already a {@code @Component}, exactly as every other
 * adapter-to-port binding already works in this codebase.
 */
@Configuration(proxyBeanMethods = false)
public class FundComparisonOptionsApplicationConfig {

  @Bean
  FundComparisonOptionsUseCase fundComparisonOptionsUseCase(
      FundComparisonOptionsSourcePort sourcePort) {
    return new FundComparisonOptionsUseCase(sourcePort);
  }
}
