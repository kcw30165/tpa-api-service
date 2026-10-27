package com.bct.ngtpa.apiservice.config.account;

import com.bct.ngtpa.apiservice.application.service.account.AccountIdentifierFormatter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AccountIdentifierConfig {

  @Bean
  @ConfigurationProperties("account-identifiers")
  AccountIdentifierProperties accountIdentifierProperties() {
    return new AccountIdentifierProperties();
  }

  @Bean
  AccountIdentifierFormatter accountIdentifierFormatter(AccountIdentifierProperties properties) {
    properties.validate();
    return new AccountIdentifierFormatter(
        properties.getPolicyNo().getWidth(),
        properties.getCertNo().getWidth(),
        properties.getPadCharacter().charAt(0));
  }
}
