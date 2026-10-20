package com.bct.ngtpa.apiservice.config.fundcomparison;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.bct.ngtpa.apiservice.application.port.out.AuthenticatedSessionPort;
import com.bct.ngtpa.apiservice.application.port.out.AuthenticatedSessionProtectorPort;
import com.bct.ngtpa.apiservice.application.port.out.FundComparisonOptionsSourcePort;
import com.bct.ngtpa.apiservice.application.port.out.TransientJsonPort;
import com.bct.ngtpa.apiservice.application.usecase.fundcomparison.FundComparisonOptionsCacheService;
import com.bct.ngtpa.apiservice.application.usecase.fundcomparison.FundComparisonOptionsUseCase;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.AuthenticatedSessionConcurrencyService;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** Fund Comparison M5.3: the options configuration wires the session cache into the use case. */
class FundComparisonOptionsApplicationConfigTest {

  @Test
  void wiresTheCacheServiceAndTheOptionsUseCase() {
    new ApplicationContextRunner()
        .withBean(
            FundComparisonOptionsSourcePort.class,
            () -> mock(FundComparisonOptionsSourcePort.class))
        .withBean(AuthenticatedSessionPort.class, () -> mock(AuthenticatedSessionPort.class))
        .withBean(
            AuthenticatedSessionProtectorPort.class,
            () -> mock(AuthenticatedSessionProtectorPort.class))
        .withBean(
            AuthenticatedSessionConcurrencyService.class,
            () -> mock(AuthenticatedSessionConcurrencyService.class))
        .withBean(TransientJsonPort.class, () -> mock(TransientJsonPort.class))
        .withBean(Clock.class, Clock::systemUTC)
        .withUserConfiguration(FundComparisonOptionsApplicationConfig.class)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).hasSingleBean(FundComparisonOptionsCacheService.class);
              assertThat(context).hasSingleBean(FundComparisonOptionsUseCase.class);
            });
  }
}
