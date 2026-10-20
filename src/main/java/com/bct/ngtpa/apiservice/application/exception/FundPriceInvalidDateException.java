package com.bct.ngtpa.apiservice.application.exception;

/**
 * Thrown only by the shared TRPGetFundPrice adapter ({@code ApimFundPriceSourceAdapter}) when the
 * downstream {@code err-message} is exactly {@code err.tpa.invalid.date}, per
 * docs/design/13-fund-comparison/09-options-session-cache-change.md D6 (sub-task 5.5, script 096).
 *
 * <p>Every TRPGetFundPrice caller (Fund Price query, Fund Price export, Fund Comparison prices)
 * shares this one adapter method, so this exception is deliberately feature-agnostic: it is mapped
 * by a dedicated {@code ApiExceptionHandler} method to HTTP 400 VALIDATION_FAILED {@code
 * err.fund-price.request.invalid} for Fund Price callers, and translated by {@code
 * FundComparisonPricesUseCase} into {@code FundComparisonRequestInvalidException} for Fund
 * Comparison. {@code ApimFundPriceModeSourceAdapter} (TRPGetFundPriceMode) is a separate adapter
 * and is unaffected; its own {@code err.tpa.invalid.date} still maps to {@link
 * FundPriceRetrievalException} (HTTP 503).
 */
public final class FundPriceInvalidDateException extends RuntimeException {

  private FundPriceInvalidDateException(String message) {
    super(message);
  }

  public static FundPriceInvalidDateException forInvalidDate() {
    return new FundPriceInvalidDateException(
        "Downstream rejected the requested date range (err.tpa.invalid.date)");
  }
}
