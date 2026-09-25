package com.bct.ngtpa.apiservice.application.usecase.fundprice;

import com.bct.ngtpa.apiservice.application.dto.fundprice.FundPriceExportResult;

/** Mirrors {@link FundPriceQueryUseCaseResult}'s exact shape for the export path. */
public record FundPriceExportUseCaseResult(
    FundPriceUseCaseOutcome outcome, FundPriceExportResult exportResult) {

  public static FundPriceExportUseCaseResult applied(FundPriceExportResult result) {
    return new FundPriceExportUseCaseResult(FundPriceUseCaseOutcome.APPLIED, result);
  }

  public static FundPriceExportUseCaseResult of(FundPriceUseCaseOutcome terminalOutcome) {
    return new FundPriceExportUseCaseResult(terminalOutcome, null);
  }
}
