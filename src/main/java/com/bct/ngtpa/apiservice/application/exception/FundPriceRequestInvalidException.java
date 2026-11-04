package com.bct.ngtpa.apiservice.application.exception;

import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;

/**
 * Thrown for Fund Price FE-input validation failures (missing/malformed/out-of-range date, date not
 * in the cached available list, missing/invalid session cookie) -- HTTP 400 per
 * fund-price-error-matrix.md's "FE input errors" section.
 *
 * <p>Corrected (O-14b, script 146): no dedicated {@link
 * com.bct.ngtpa.apiservice.adapter.in.web.error.ExceptionMapping} is registered for this exception
 * type in {@code ExceptionMappingRegistryConfig}. {@code FundPriceExceptionMappings} only maps the
 * unrelated {@code FundPriceRetrievalException} and {@code FundPriceInvalidDateException} (a
 * different class, despite sharing this exception's {@code ErrorCodes.FUND_PRICE_REQUEST_INVALID}
 * error code value). {@code ExceptionMappingRegistry} resolves by nearest-superclass walk, so this
 * exception falls through to its registered superclass mapping, {@code
 * ApplicationExceptionMappings.generic()}, which always returns HTTP 400 / {@code
 * ApiStatus.BUSINESS_REJECTED} / error type {@code "BUSINESS"} -- not {@code
 * VALIDATION_FAILED}/{@code "FORM"} as the exception's name might suggest. There is no
 * {@code @ExceptionHandler} method of any kind in {@code ApiExceptionHandler}, which now has only
 * the single catch-all {@code @ExceptionHandler(Exception.class)} entry point.
 */
public class FundPriceRequestInvalidException extends ApplicationException {
  public FundPriceRequestInvalidException(String message) {
    super(ErrorCodes.FUND_PRICE_REQUEST_INVALID, message);
  }
}
