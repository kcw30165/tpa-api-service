package com.bct.ngtpa.apiservice.application.exception;

import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;

/**
 * Thrown when {@code /api/v1/account/summary} receives a non-blank {@code currency} that is not
 * present in, or cannot be validated against, the session-cached available-currency list for this
 * account (D-2/D-3 of
 * docs/refactor/3-account-endpoints-restructure/8-investgl-curr-required-addendum.md). This
 * includes both an explicit non-membership and a cache miss (missing session cookie, unusable
 * session, absent entry, undecodable entry, or an entry bound to a different account): every one of
 * those is a miss and rejected the same way.
 *
 * <p>Mapped automatically to HTTP 400 by the existing generic
 * {@code @ExceptionHandler(ApplicationException.class)} in {@code ApiExceptionHandler}, mirroring
 * {@link FundPriceRequestInvalidException}'s exact one-line shape; no dedicated handler method is
 * needed for this exception.
 */
public class AccountSummaryRequestInvalidException extends ApplicationException {
  public AccountSummaryRequestInvalidException(String message) {
    super(ErrorCodes.ACCOUNT_SUMMARY_REQUEST_INVALID, message);
  }
}
