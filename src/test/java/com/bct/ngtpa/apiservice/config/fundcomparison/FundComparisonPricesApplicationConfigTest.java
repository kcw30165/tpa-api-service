package com.bct.ngtpa.apiservice.config.fundcomparison;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.bct.ngtpa.apiservice.application.port.out.FundComparisonPriceSourcePort;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDatePort;
import com.bct.ngtpa.apiservice.application.usecase.fundcomparison.FundComparisonOptionsCacheService;
import com.bct.ngtpa.apiservice.application.usecase.fundcomparison.FundComparisonOptionsUseCase;
import com.bct.ngtpa.apiservice.application.usecase.fundcomparison.FundComparisonPricesUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** Fund Comparison M5.4: the prices configuration wires the single prices use case. */
class FundComparisonPricesApplicationConfigTest {

  @Test
  void wiresThePricesUseCaseWithTheOptionsUseCaseAndTheCache() {
    new ApplicationContextRunner()
        .withBean(
            FundComparisonPriceSourcePort.class, () -> mock(FundComparisonPriceSourcePort.class))
        .withBean(ReferenceDatePort.class, () -> mock(ReferenceDatePort.class))
        .withBean(
            FundComparisonOptionsUseCase.class, () -> mock(FundComparisonOptionsUseCase.class))
        .withBean(
            FundComparisonOptionsCacheService.class,
            () -> mock(FundComparisonOptionsCacheService.class))
        .withUserConfiguration(FundComparisonPricesApplicationConfig.class)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).hasSingleBean(FundComparisonPricesUseCase.class);
            });
  }
}
