package com.bct.ngtpa.apiservice.application.usecase.session.authenticated;

import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import java.util.Objects;

/** Requests the normal user-logout transition for an authenticated session. */
public record LogoutActiveAuthenticatedSessionCommand(
    AuthenticatedSessionId authenticatedSessionId, String correlationId) {
  public LogoutActiveAuthenticatedSessionCommand {
    Objects.requireNonNull(authenticatedSessionId, "authenticatedSessionId must not be null");
    correlationId = correlationId == null || correlationId.isBlank() ? null : correlationId.trim();
  }

  @Override
  public String toString() {
    return "LogoutActiveAuthenticatedSessionCommand[authenticatedSessionId=<redacted>, correlationId="
        + (correlationId == null ? null : "<redacted>")
        + "]";
  }
}
