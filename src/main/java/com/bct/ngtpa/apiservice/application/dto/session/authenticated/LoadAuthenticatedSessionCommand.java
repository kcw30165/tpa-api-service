package com.bct.ngtpa.apiservice.application.dto.session.authenticated;

import java.util.Objects;

public record LoadAuthenticatedSessionCommand(
    AuthenticatedSessionId authenticatedSessionId, String correlationId) {
  public LoadAuthenticatedSessionCommand {
    Objects.requireNonNull(authenticatedSessionId);
    correlationId = optional(correlationId);
  }

  private static String optional(String v) {
    return v == null || v.isBlank() ? null : v.trim();
  }

  @Override
  public String toString() {
    return "LoadAuthenticatedSessionCommand[id=<redacted>, correlationId=" + correlationId + "]";
  }
}
