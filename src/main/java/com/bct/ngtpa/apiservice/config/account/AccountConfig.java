package com.bct.ngtpa.apiservice.config.account;

import com.bct.ngtpa.apiservice.adapter.in.web.account.AccountWebMapper;
import com.bct.ngtpa.apiservice.application.port.out.AccountLookupPort;
import com.bct.ngtpa.apiservice.application.usecase.account.AuthenticatedAccountLookupAdapter;
import com.bct.ngtpa.apiservice.application.usecase.account.GetAccountService;
import com.bct.ngtpa.apiservice.application.usecase.account.GetAccountUseCase;
import com.bct.ngtpa.apiservice.application.usecase.account.GetAccountsService;
import com.bct.ngtpa.apiservice.application.usecase.account.GetAccountsUseCase;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.LoadAndTouchAuthenticatedSessionUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the account lookup endpoints' beans, mirroring the existing AuthenticatedSessionConfig
 * pattern.
 */
@Configuration
public class AccountConfig {

  @Bean
  AccountLookupPort accountLookupPort(LoadAndTouchAuthenticatedSessionUseCase lifecycle) {
    return new AuthenticatedAccountLookupAdapter(lifecycle);
  }

  @Bean
  GetAccountUseCase getAccountUseCase(AccountLookupPort accountLookupPort) {
    return new GetAccountService(accountLookupPort);
  }

  @Bean
  GetAccountsUseCase getAccountsUseCase(AccountLookupPort accountLookupPort) {
    return new GetAccountsService(accountLookupPort);
  }

  @Bean
  AccountWebMapper accountWebMapper() {
    return new AccountWebMapper();
  }
}
