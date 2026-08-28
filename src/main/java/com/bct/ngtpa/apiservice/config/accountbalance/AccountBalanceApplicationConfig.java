package com.bct.ngtpa.apiservice.config.accountbalance;

import com.bct.ngtpa.apiservice.application.exception.AccountBalanceRetrievalException;
import com.bct.ngtpa.apiservice.application.port.in.*;
import com.bct.ngtpa.apiservice.application.port.out.*;
import com.bct.ngtpa.apiservice.application.usecase.GetAccountBalanceService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

@Configuration(proxyBeanMethods = false)
public class AccountBalanceApplicationConfig {
  @Bean
  GetAccountBalanceUseCase getAccountBalanceUseCase(ReferenceDatePort referenceDatePort,
      GeneralMetadataResolver generalMetadataResolver, AccountBalanceSourcePort accountBalanceSourcePort,
      AccountBalanceAssembler accountBalanceAssembler) {
    return new GetAccountBalanceService(referenceDatePort, generalMetadataResolver,
        accountBalanceSourcePort, accountBalanceAssembler);
  }

  @Bean
  AccountBalanceAssembler pendingPhaseFourAccountBalanceAssembler() {
    return (command, referenceDate, metadata, rawData) ->
        Mono.error(AccountBalanceRetrievalException.systemFailure());
  }
}
