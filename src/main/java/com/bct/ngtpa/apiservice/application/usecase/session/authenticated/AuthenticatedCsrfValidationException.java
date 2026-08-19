package com.bct.ngtpa.apiservice.application.usecase.session.authenticated;

/**
 * Typed internal signal: the authenticated session is usable but its submitted CSRF token is
 * invalid.
 */
public final class AuthenticatedCsrfValidationException extends RuntimeException {
  public AuthenticatedCsrfValidationException() {
    super("Authenticated CSRF validation failed");
  }
}
