package com.bct.ngtpa.apiservice.application.usecase.fundprice;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Confirms a deliberate 3-outcome shape (APPLIED, INVALID_DATE, SYSTEM_FAILURE) -- a departure from
 * FundPriceModeUseCaseOutcome's 2-outcome collapsing style. Date validation failures are a genuine
 * HTTP-400-shaped case per fund-price-error-matrix.md, distinct from the 503 SYSTEM_FAILURE case
 * for session/downstream failures. This test exists specifically to catch a future accidental
 * collapse back to 2 outcomes.
 */
class FundPriceUseCaseOutcomeTest {

  @Test
  void hasExactlyThreeOutcomes() {
    assertThat(FundPriceUseCaseOutcome.values()).hasSize(3);
    assertThat(FundPriceUseCaseOutcome.values())
        .containsExactlyInAnyOrder(
            FundPriceUseCaseOutcome.APPLIED,
            FundPriceUseCaseOutcome.INVALID_DATE,
            FundPriceUseCaseOutcome.SYSTEM_FAILURE);
  }
}
