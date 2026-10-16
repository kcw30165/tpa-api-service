package com.bct.ngtpa.apiservice.config.fundcomparison;

import com.bct.ngtpa.apiservice.adapter.in.web.fundcomparison.mapper.FundComparisonOptionsMapper;
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
 * <p>Neither {@link FundComparisonOptionsSourcePort} nor {@link FundComparisonOptionsMapper} needs
 * an explicit {@code @Bean} method: {@code ApimFundComparisonOptionsSourceAdapter} and {@code
 * FundComparisonOptionsMapper} are already {@code @Component}-annotated, so Spring resolves them by
 * type automatically via component scan, exactly as every other adapter/mapper-to-port binding
 * already works in this codebase.
 */
@Configuration(proxyBeanMethods = false)
public class FundComparisonOptionsApplicationConfig {

  @Bean
  FundComparisonOptionsUseCase fundComparisonOptionsUseCase(
      FundComparisonOptionsSourcePort sourcePort, FundComparisonOptionsMapper mapper) {
    return new FundComparisonOptionsUseCase(sourcePort, mapper);
  }
}
