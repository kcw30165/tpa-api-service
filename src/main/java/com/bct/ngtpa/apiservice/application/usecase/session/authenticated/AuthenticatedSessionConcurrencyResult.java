package com.bct.ngtpa.apiservice.application.usecase.session.authenticated;

import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSession;

/**
 * {@code session} is non-null only when {@code outcome} is {@link
 * AuthenticatedSessionConcurrencyOutcome#APPLIED}; every other outcome carries no session.
 */
public record AuthenticatedSessionConcurrencyResult(
    AuthenticatedSessionConcurrencyOutcome outcome, AuthenticatedSession session) {

  public AuthenticatedSessionConcurrencyResult {
    if (outcome == null) {
      throw new IllegalArgumentException("outcome must not be null");
    }
    boolean requiresSession = outcome == AuthenticatedSessionConcurrencyOutcome.APPLIED;
    if (requiresSession && session == null) {
      throw new IllegalArgumentException("APPLIED result must carry a session");
    }
    if (!requiresSession && session != null) {
      throw new IllegalArgumentException("non-APPLIED result must not carry a session");
    }
  }

  public static AuthenticatedSessionConcurrencyResult applied(AuthenticatedSession session) {
    return new AuthenticatedSessionConcurrencyResult(
        AuthenticatedSessionConcurrencyOutcome.APPLIED, session);
  }

  public static AuthenticatedSessionConcurrencyResult of(
      AuthenticatedSessionConcurrencyOutcome terminalOutcome) {
    if (terminalOutcome == AuthenticatedSessionConcurrencyOutcome.APPLIED) {
      throw new IllegalArgumentException("use applied(session) for the APPLIED outcome");
    }
    return new AuthenticatedSessionConcurrencyResult(terminalOutcome, null);
  }
}
