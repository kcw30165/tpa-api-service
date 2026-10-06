package com.bct.ngtpa.apiservice.application.exception;

import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;

/**
 * Thrown for Fund Price FE-input validation failures (missing/malformed/out-of-range date, date not
 * in the cached available list, missing/invalid session cookie) -- HTTP 400 per
 * fund-price-error-matrix.md's "FE input errors" section.
 *
 * <p>Mapped automatically to HTTP 400 by the existing generic
 * {@code @ExceptionHandler(ApplicationException.class)} in {@code ApiExceptionHandler} -- mirrors
 * {@code InvalidContributionRequestException}'s exact one-line shape (confirmed real precedent); no
 * dedicated handler method is needed for this exception.
 */
public class FundPriceRequestInvalidException extends ApplicationException {
  public FundPriceRequestInvalidException(String message) {
    super(ErrorCodes.FUND_PRICE_REQUEST_INVALID, message);
  }
}
