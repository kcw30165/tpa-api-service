package com.bct.ngtpa.apiservice.config.accountbalance;

import com.bct.ngtpa.apiservice.application.port.in.GetAccountBalanceUseCase;
import com.bct.ngtpa.apiservice.application.port.out.AccountBalanceDataPort;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDatePort;
import com.bct.ngtpa.apiservice.application.usecase.GetAccountBalanceService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class AccountBalanceApplicationConfig {
  @Bean
  GetAccountBalanceUseCase getAccountBalanceUseCase(
      ReferenceDatePort referenceDatePort, AccountBalanceDataPort accountBalanceDataPort) {
    return new GetAccountBalanceService(referenceDatePort, accountBalanceDataPort);
  }
}
