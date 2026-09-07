package com.bct.ngtpa.apiservice.application.service.accountbalance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.exception.AccountBalanceRetrievalException;
import java.math.BigDecimal;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;

class AccountBalancePrecisionRendererPreservationTest {
  private final AccountBalancePrecisionRenderer renderer = new AccountBalancePrecisionRenderer();

  @Test
  void preservesExistingGroupingAndExactScaleBehavior() {
    assertThat(renderer.render(new BigDecimal("5092583.02"), BigInteger.valueOf(2)))
        .isEqualTo("5,092,583.02");
    assertThat(renderer.render(BigDecimal.ZERO, BigInteger.valueOf(2))).isEqualTo("0.00");
  }

  @Test
  void preservesFeatureSpecificFailureTranslation() {
    assertThatThrownBy(() -> renderer.render(new BigDecimal("10.125"), BigInteger.valueOf(2)))
        .isInstanceOf(AccountBalanceRetrievalException.class);
  }
}
