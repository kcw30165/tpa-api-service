package com.bct.ngtpa.apiservice.application.dto.session.authenticated;

import java.util.Objects;

public record AuthenticatedSessionCasUpdateResult(AuthenticatedSessionCasUpdateOutcome outcome) {

  public AuthenticatedSessionCasUpdateResult {
    Objects.requireNonNull(outcome, "outcome must not be null");
  }

  public static AuthenticatedSessionCasUpdateResult of(AuthenticatedSessionCasUpdateOutcome outcome) {
    return new AuthenticatedSessionCasUpdateResult(outcome);
  }
}
