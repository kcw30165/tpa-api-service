package com.bct.ngtpa.apiservice.application.exception;

import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;

/**
 * Thrown for every Fund Comparison retrieval failure: missing/malformed downstream envelope,
 * nonblank downstream err-message, structurally unusable options/price content, or no valid
 * time-range token remaining after normalization. A single generic failure factory is used -- there
 * is no branch on specific downstream error text, mirroring {@link FundPriceRetrievalException}
 * exactly, per docs/design/13-fund-comparison/07-implementation-plan.md §7.2 sub-task 1.1.
 */
public final class FundComparisonRetrievalException extends RuntimeException {

  private FundComparisonRetrievalException(String message) {
    super(message);
  }

  public static FundComparisonRetrievalException systemFailure() {
    return new FundComparisonRetrievalException("Fund Comparison retrieval unavailable");
  }

  public String getErrorCode() {
    return ErrorCodes.FUND_COMPARISON_RETRIEVAL_UNAVAILABLE;
  }
}
