package com.bct.ngtpa.apiservice.config.accountsummary;

import com.bct.ngtpa.apiservice.application.port.in.AccountBalanceAssembler;
import com.bct.ngtpa.apiservice.application.port.in.GeneralMetadataResolver;
import com.bct.ngtpa.apiservice.application.port.in.GetAccountSummaryUseCase;
import com.bct.ngtpa.apiservice.application.port.out.AccountBalanceSourcePort;
import com.bct.ngtpa.apiservice.application.port.out.AccountGainLossSourcePort;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDatePort;
import com.bct.ngtpa.apiservice.application.service.accountgainloss.AccountGainLossAssembler;
import com.bct.ngtpa.apiservice.application.service.accountsummary.AccountSummaryAssembler;
import com.bct.ngtpa.apiservice.application.usecase.GetAccountSummaryService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Consumes the existing {@link AccountBalanceAssembler} bean (registered by
 * AccountBalanceApplicationConfig) and the existing {@link AccountGainLossAssembler} bean
 * (registered by AccountGainLossApplicationConfig) unmodified. Only the two beans below are new.
 */
@Configuration(proxyBeanMethods = false)
public class AccountSummaryApplicationConfig {

  @Bean
  AccountSummaryAssembler accountSummaryAssembler() {
    return new AccountSummaryAssembler();
  }

  @Bean
  GetAccountSummaryUseCase getAccountSummaryUseCase(
      ReferenceDatePort referenceDatePort,
      GeneralMetadataResolver generalMetadataResolver,
      AccountBalanceSourcePort accountBalanceSourcePort,
      AccountGainLossSourcePort accountGainLossSourcePort,
      AccountBalanceAssembler accountBalanceAssembler,
      AccountGainLossAssembler accountGainLossAssembler,
      AccountSummaryAssembler accountSummaryAssembler) {
    return new GetAccountSummaryService(
        referenceDatePort,
        generalMetadataResolver,
        accountBalanceSourcePort,
        accountGainLossSourcePort,
        accountBalanceAssembler,
        accountGainLossAssembler,
        accountSummaryAssembler);
  }
}
