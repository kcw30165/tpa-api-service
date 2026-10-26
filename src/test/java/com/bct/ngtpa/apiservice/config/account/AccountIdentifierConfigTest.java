package com.bct.ngtpa.apiservice.config.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.service.account.AccountIdentifierFormatter;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.account.LoginAccountRecordValidationService;
import com.bct.ngtpa.apiservice.config.auth.login.LoginAccountValidationConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AccountIdentifierConfigTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
          .withUserConfiguration(AccountIdentifierConfig.class, LoginAccountValidationConfig.class)
          .withPropertyValues(
              "account-identifiers.policy-no.width=6",
              "account-identifiers.cert-no.width=3",
              "account-identifiers.pad-character=7");

  @Test
  void bindsGlobalAccountIdentifierPropertiesToTheSharedFormatter() {
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(AccountIdentifierFormatter.class);
          assertThat(context).hasSingleBean(LoginAccountRecordValidationService.class);
          assertThat(context.getBean(AccountIdentifierFormatter.class).format("42", "8"))
              .isEqualTo("777742-778");
        });
  }
}
