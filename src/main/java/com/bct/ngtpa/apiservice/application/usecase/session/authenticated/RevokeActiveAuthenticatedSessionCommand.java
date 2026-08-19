package com.bct.ngtpa.apiservice.application.usecase.session.authenticated;

import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import java.util.Objects;

public record RevokeActiveAuthenticatedSessionCommand(
    AuthenticatedSessionId authenticatedSessionId, String reason, String correlationId) {
  public RevokeActiveAuthenticatedSessionCommand {
    Objects.requireNonNull(authenticatedSessionId);
    if (reason == null || reason.isBlank()) throw new IllegalArgumentException("reason required");
    reason = reason.trim();
    correlationId = correlationId == null || correlationId.isBlank() ? null : correlationId.trim();
  }
}
