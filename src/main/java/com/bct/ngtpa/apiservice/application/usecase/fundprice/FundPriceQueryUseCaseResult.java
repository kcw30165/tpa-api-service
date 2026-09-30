package com.bct.ngtpa.apiservice.application.usecase.fundprice;

import com.bct.ngtpa.apiservice.application.dto.fundprice.FundPriceQueryResult;

/** {@code queryResult} is non-null only when {@code outcome} is APPLIED. */
public record FundPriceQueryUseCaseResult(
    FundPriceUseCaseOutcome outcome, FundPriceQueryResult queryResult) {

  public static FundPriceQueryUseCaseResult applied(FundPriceQueryResult result) {
    return new FundPriceQueryUseCaseResult(FundPriceUseCaseOutcome.APPLIED, result);
  }

  public static FundPriceQueryUseCaseResult of(FundPriceUseCaseOutcome terminalOutcome) {
    return new FundPriceQueryUseCaseResult(terminalOutcome, null);
  }
}
