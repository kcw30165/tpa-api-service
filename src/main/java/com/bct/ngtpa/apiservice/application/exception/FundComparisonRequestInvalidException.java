package com.bct.ngtpa.apiservice.application.exception;

import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;

/**
 * Thrown when a Fund Comparison request fails public input validation (invalid/missing time range,
 * missing/blank fund codes, more than five unique fund codes).
 *
 * <p>Corrected (O-14b, script 146): mapped to HTTP 400 / {@code ApiStatus.VALIDATION_FAILED} /
 * error type {@code "VALIDATION"} by its own dedicated {@link
 * com.bct.ngtpa.apiservice.adapter.in.web.error.ExceptionMapping}, {@code
 * ApplicationExceptionMappings.invalidFundComparisonRequest()}, registered in the shared {@code
 * ExceptionMappingRegistry} (not a per-type {@code @ExceptionHandler} method in {@code
 * ApiExceptionHandler}, which now has only the single catch-all
 * {@code @ExceptionHandler(Exception.class)} entry point) -- mirrors {@code
 * InvalidContributionRequestException}'s and {@code InvalidNotificationRequestException}'s
 * dedicated-mapping precedent, NOT the generic {@code ApplicationExceptionMappings.generic()}
 * fallback, which hardcodes {@code ApiStatus.BUSINESS_REJECTED} and error type {@code "BUSINESS"},
 * and would not satisfy docs/design/13-fund-comparison/04-validation-and-error-handling.md section
 * 4.3's confirmed public mapping (API status: VALIDATION_FAILED; error type: VALIDATION).
 */
public class FundComparisonRequestInvalidException extends ApplicationException {

  public FundComparisonRequestInvalidException(String message) {
    super(ErrorCodes.FUND_COMPARISON_REQUEST_INVALID, message);
  }
}
