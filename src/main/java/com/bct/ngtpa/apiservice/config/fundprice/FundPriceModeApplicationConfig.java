package com.bct.ngtpa.apiservice.config.fundprice;

import com.bct.ngtpa.apiservice.application.port.out.FundPriceModeSourcePort;
import com.bct.ngtpa.apiservice.application.usecase.fundprice.FundPriceModeUseCase;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.AuthenticatedSessionConcurrencyService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires {@link FundPriceModeUseCase}, mirroring {@code AccountBalanceApplicationConfig}'s pattern:
 * the use case itself carries no Spring annotation (application layer must not depend on Spring
 * Framework), and is constructed explicitly here from its port dependencies.
 *
 * <p>{@link FundPriceModeSourcePort} needs no explicit {@code @Bean} method: {@code
 * ApimFundPriceModeSourceAdapter} is already a {@code @Component}-annotated adapter that now {@code
 * implements FundPriceModeSourcePort}, so Spring resolves it by type automatically via component
 * scan, exactly as every other adapter-to-port binding already works in this codebase.
 */
@Configuration
public class FundPriceModeApplicationConfig {

  @Bean
  FundPriceModeUseCase fundPriceModeUseCase(
      FundPriceModeSourcePort sourcePort,
      AuthenticatedSessionConcurrencyService concurrencyService) {
    return new FundPriceModeUseCase(sourcePort, concurrencyService);
  }
}
