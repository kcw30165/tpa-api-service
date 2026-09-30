package com.bct.ngtpa.apiservice.application.usecase.fundprice;

/**
 * Deliberately 3 outcomes -- a departure from {@code FundPriceModeUseCaseOutcome}'s 2-outcome
 * collapsing style. Date validation failures are a genuine HTTP-400-shaped case per
 * fund-price-error-matrix.md, distinct from the 503 SYSTEM_FAILURE case for session/downstream
 * failures.
 */
public enum FundPriceUseCaseOutcome {
  APPLIED,
  INVALID_DATE,
  SYSTEM_FAILURE
}
