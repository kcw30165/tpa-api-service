package com.bct.ngtpa.apiservice.config.accountswitch;

import com.bct.ngtpa.apiservice.adapter.in.web.accountswitch.AccountSwitchWebMapper;
import com.bct.ngtpa.apiservice.application.port.out.AccountSwitchLookupPort;
import com.bct.ngtpa.apiservice.application.usecase.accountswitch.AuthenticatedAccountSwitchLookupAdapter;
import com.bct.ngtpa.apiservice.application.usecase.accountswitch.SwitchAccountService;
import com.bct.ngtpa.apiservice.application.usecase.accountswitch.SwitchAccountUseCase;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.LoadAndTouchAuthenticatedSessionUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the account-switch feature's beans. Mirrors the existing pattern in
 * AuthenticatedSessionConfig, which wires AuthenticatedAccountContextResolver using the same
 * {@link LoadAndTouchAuthenticatedSessionUseCase} bean.
 */
@Configuration
public class AccountSwitchConfig {

  @Bean
  AccountSwitchLookupPort accountSwitchLookupPort(LoadAndTouchAuthenticatedSessionUseCase lifecycle) {
    return new AuthenticatedAccountSwitchLookupAdapter(lifecycle);
  }

  @Bean
  SwitchAccountUseCase switchAccountUseCase(AccountSwitchLookupPort accountSwitchLookupPort) {
    return new SwitchAccountService(accountSwitchLookupPort);
  }

  @Bean
  AccountSwitchWebMapper accountSwitchWebMapper() {
    return new AccountSwitchWebMapper();
  }
}
