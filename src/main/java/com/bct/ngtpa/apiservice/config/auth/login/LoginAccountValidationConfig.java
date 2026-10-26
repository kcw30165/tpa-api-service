package com.bct.ngtpa.apiservice.config.auth.login;

import com.bct.ngtpa.apiservice.application.service.account.AccountIdentifierFormatter;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.account.LoginAccountRecordValidationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LoginAccountValidationConfig {

  @Bean
  LoginAccountRecordValidationService loginAccountRecordValidationService(
      AccountIdentifierFormatter accountIdentifierFormatter) {
    return new LoginAccountRecordValidationService(accountIdentifierFormatter);
  }
}
