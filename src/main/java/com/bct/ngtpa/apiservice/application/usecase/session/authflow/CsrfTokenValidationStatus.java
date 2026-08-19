package com.bct.ngtpa.apiservice.application.usecase.session.authflow;

/**
 * Shared token-comparison outcomes. Session and workflow validity remain owned by their lifecycle
 * services.
 */
public enum CsrfTokenValidationStatus {
  VALID,
  TOKEN_MISSING,
  TOKEN_HASH_MISSING,
  TOKEN_MISMATCH
}
