package com.bct.ngtpa.apiservice.application.dto.passwordvalidation;

/** Internal outcomes only; no downstream text, fail counts or public HTTP mappings. */
public enum PasswordCheckResult {
  CORRECT,
  INCORRECT,
  ACCOUNT_LOCKED,
  CUSTOMER_NOT_REGISTERED,
  UNAVAILABLE
}
