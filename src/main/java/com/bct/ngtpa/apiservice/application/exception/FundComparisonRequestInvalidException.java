package com.bct.ngtpa.apiservice.application.exception;

import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;

/**
 * Thrown when a Fund Comparison request fails public input validation (invalid/missing time range,
 * missing/blank fund codes, more than five unique fund codes). Mirrors {@link
 * FundPriceRequestInvalidException}'s exact one-line shape (confirmed real precedent, script 014's
 * investigation): mapped automatically to HTTP 400 by the existing generic
 * {@code @ExceptionHandler(ApplicationException.class)} in {@code ApiExceptionHandler} -- no
 * dedicated handler method is needed for this exception, per
 * docs/design/13-fund-comparison/07-implementation-plan.md §7.2 sub-task 1.1.
 */
public class FundComparisonRequestInvalidException extends ApplicationException {

  public FundComparisonRequestInvalidException(String message) {
    super(ErrorCodes.FUND_COMPARISON_REQUEST_INVALID, message);
  }
}
