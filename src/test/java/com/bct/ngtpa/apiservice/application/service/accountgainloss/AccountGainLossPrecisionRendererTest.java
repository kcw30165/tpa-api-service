package com.bct.ngtpa.apiservice.application.service.accountgainloss;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.exception.AccountGainLossRetrievalException;
import java.math.BigDecimal;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;

class AccountGainLossPrecisionRendererTest {
  private final AccountGainLossPrecisionRenderer renderer = new AccountGainLossPrecisionRenderer();

  @Test
  void delegatesExactPresentation() {
    assertThat(renderer.render(new BigDecimal("5536439.03"), BigInteger.valueOf(2)))
        .isEqualTo("5,536,439.03");
  }

  @Test
  void translatesPresentationFailureToGainLossFailure() {
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> renderer.render(new BigDecimal("10.125"), BigInteger.valueOf(2)))
        .isInstanceOfSatisfying(
            AccountGainLossRetrievalException.class,
            ex ->
                assertThat(ex.reason())
                    .isEqualTo(AccountGainLossRetrievalException.Reason.SYSTEM_FAILURE));
  }
}
