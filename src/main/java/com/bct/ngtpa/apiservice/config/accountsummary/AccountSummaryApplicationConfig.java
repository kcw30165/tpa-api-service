package com.bct.ngtpa.apiservice.config.accountsummary;

import com.bct.ngtpa.apiservice.application.port.in.AccountBalanceAssembler;
import com.bct.ngtpa.apiservice.application.port.in.GeneralMetadataResolver;
import com.bct.ngtpa.apiservice.application.port.in.GetAccountSummaryUseCase;
import com.bct.ngtpa.apiservice.application.port.out.AccountBalanceSourcePort;
import com.bct.ngtpa.apiservice.application.port.out.AccountGainLossSourcePort;
import com.bct.ngtpa.apiservice.application.port.out.AuthenticatedSessionPort;
import com.bct.ngtpa.apiservice.application.port.out.AuthenticatedSessionProtectorPort;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDatePort;
import com.bct.ngtpa.apiservice.application.port.out.TransientJsonPort;
import com.bct.ngtpa.apiservice.application.service.accountgainloss.AccountGainLossAssembler;
import com.bct.ngtpa.apiservice.application.service.accountsummary.AccountSummaryAssembler;
import com.bct.ngtpa.apiservice.application.usecase.GetAccountSummaryService;
import com.bct.ngtpa.apiservice.application.usecase.accountsummary.AccountSummaryCurrencyCacheService;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.AuthenticatedSessionConcurrencyService;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Consumes the existing {@link AccountBalanceAssembler} bean (registered by
 * AccountBalanceApplicationConfig) and the existing {@link AccountGainLossAssembler} bean
 * (registered by AccountGainLossApplicationConfig) unmodified. Also consumes the existing {@link
 * AuthenticatedSessionPort}, {@link AuthenticatedSessionProtectorPort}, {@link
 * AuthenticatedSessionConcurrencyService}, {@link TransientJsonPort}, and {@link Clock} beans
 * already registered elsewhere for the generic authenticated-session mechanism, mirroring exactly
 * how {@code FundComparisonOptionsCacheService} is wired.
 */
@Configuration(proxyBeanMethods = false)
public class AccountSummaryApplicationConfig {

  @Bean
  AccountSummaryAssembler accountSummaryAssembler() {
    return new AccountSummaryAssembler();
  }

  @Bean
  AccountSummaryCurrencyCacheService accountSummaryCurrencyCacheService(
      AuthenticatedSessionPort authenticatedSessionPort,
      AuthenticatedSessionProtectorPort authenticatedSessionProtectorPort,
      AuthenticatedSessionConcurrencyService authenticatedSessionConcurrencyService,
      TransientJsonPort transientJsonPort,
      Clock clock) {
    return new AccountSummaryCurrencyCacheService(
        authenticatedSessionPort,
        authenticatedSessionProtectorPort,
        authenticatedSessionConcurrencyService,
        transientJsonPort,
        clock);
  }

  @Bean
  GetAccountSummaryUseCase getAccountSummaryUseCase(
      ReferenceDatePort referenceDatePort,
      GeneralMetadataResolver generalMetadataResolver,
      AccountBalanceSourcePort accountBalanceSourcePort,
      AccountGainLossSourcePort accountGainLossSourcePort,
      AccountBalanceAssembler accountBalanceAssembler,
      AccountGainLossAssembler accountGainLossAssembler,
      AccountSummaryAssembler accountSummaryAssembler,
      AccountSummaryCurrencyCacheService accountSummaryCurrencyCacheService) {
    return new GetAccountSummaryService(
        referenceDatePort,
        generalMetadataResolver,
        accountBalanceSourcePort,
        accountGainLossSourcePort,
        accountBalanceAssembler,
        accountGainLossAssembler,
        accountSummaryAssembler,
        accountSummaryCurrencyCacheService);
  }
}
