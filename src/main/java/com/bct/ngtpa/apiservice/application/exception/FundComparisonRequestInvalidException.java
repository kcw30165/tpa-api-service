package com.bct.ngtpa.apiservice.application.exception;

import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;

/**
 * Thrown when a Fund Comparison request fails public input validation (invalid/missing time range,
 * missing/blank fund codes, more than five unique fund codes).
 *
 * <p>Corrected (fix, script 069): mapped to HTTP 400 / {@code ApiStatus.VALIDATION_FAILED} / error
 * type {@code "VALIDATION"} by its OWN dedicated {@code @ExceptionHandler} method in {@code
 * ApiExceptionHandler}, mirroring {@code InvalidContributionRequestException}'s and {@code
 * InvalidNotificationRequestException}'s confirmed dedicated-handler precedent -- NOT the generic
 * {@code @ExceptionHandler(ApplicationException.class)} fallback, which hardcodes {@code
 * ApiStatus.BUSINESS_REJECTED} and error type {@code "BUSINESS"}, and would not satisfy
 * docs/design/13-fund-comparison/04-validation-and-error-handling.md section 4.3's confirmed public
 * mapping (API status: VALIDATION_FAILED; error type: VALIDATION).
 */
public class FundComparisonRequestInvalidException extends ApplicationException {

  public FundComparisonRequestInvalidException(String message) {
    super(ErrorCodes.FUND_COMPARISON_REQUEST_INVALID, message);
  }
}
