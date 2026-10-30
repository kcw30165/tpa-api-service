package com.bct.ngtpa.apiservice.application.usecase.session.authenticated;

import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSession;

/**
 * Applied writes and ineligible observations carry their current session. Existing updateIfUsable
 * callers retain their session-free SESSION_NOT_USABLE result.
 */
public record AuthenticatedSessionConcurrencyResult(
    AuthenticatedSessionConcurrencyOutcome outcome, AuthenticatedSession session) {

  public AuthenticatedSessionConcurrencyResult {
    if (outcome == null) {
      throw new IllegalArgumentException("outcome must not be null");
    }
    boolean requiresSession =
        outcome == AuthenticatedSessionConcurrencyOutcome.APPLIED
            || outcome == AuthenticatedSessionConcurrencyOutcome.UNCHANGED;
    if (requiresSession && session == null) {
      throw new IllegalArgumentException("APPLIED or UNCHANGED result must carry a session");
    }
    if (!requiresSession && session != null) {
      throw new IllegalArgumentException(
          "non-APPLIED/non-UNCHANGED result must not carry a session");
    }
  }

  public static AuthenticatedSessionConcurrencyResult applied(AuthenticatedSession session) {
    return new AuthenticatedSessionConcurrencyResult(
        AuthenticatedSessionConcurrencyOutcome.APPLIED, session);
  }

  public static AuthenticatedSessionConcurrencyResult of(
      AuthenticatedSessionConcurrencyOutcome terminalOutcome) {
    if (terminalOutcome == AuthenticatedSessionConcurrencyOutcome.APPLIED
        || terminalOutcome == AuthenticatedSessionConcurrencyOutcome.UNCHANGED) {
      throw new IllegalArgumentException(
          "use applied(session) or unchanged(session) for an observation");
    }
    return new AuthenticatedSessionConcurrencyResult(terminalOutcome, null);
  }

  public static AuthenticatedSessionConcurrencyResult unchanged(AuthenticatedSession session) {
    return new AuthenticatedSessionConcurrencyResult(
        AuthenticatedSessionConcurrencyOutcome.UNCHANGED, session);
  }
}
