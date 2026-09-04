package com.bct.ngtpa.apiservice.application.service.accountbalance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.exception.AccountBalanceRetrievalException;
import org.junit.jupiter.api.Test;

class AccountBalanceDynamicDescriptionResolverPreservationTest {
  private final AccountBalanceDynamicDescriptionResolver resolver =
      new AccountBalanceDynamicDescriptionResolver();

  @Test
  void preservesWhitespaceAndLanguageSelection() {
    assertThat(resolver.resolve("en", "  English  ", "  中文  "))
        .isEqualTo("  English  ");
    assertThat(resolver.resolve("zh-HK", "  English  ", "  中文  "))
        .isEqualTo("  中文  ");
  }

  @Test
  void preservesAccountBalanceFailureType() {
    assertThatThrownBy(() -> resolver.resolve("en", " ", null))
        .isInstanceOf(AccountBalanceRetrievalException.class);
  }
}
