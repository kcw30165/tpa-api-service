package com.bct.ngtpa.apiservice.application.dto.passwordvalidation;

import com.bct.ngtpa.apiservice.application.dto.PortalAccessContext;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import java.util.Objects;

public record PasswordValidationPrecheckContext(
    AuthenticatedSessionId sessionId, PortalAccessContext context, String correlationId) {

  public PasswordValidationPrecheckContext {
    Objects.requireNonNull(sessionId, "sessionId must not be null");
    Objects.requireNonNull(context, "context must not be null");
    Objects.requireNonNull(correlationId, "correlationId must not be null");
  }

  @Override
  public String toString() {
    return "PasswordValidationPrecheckContext[<redacted>]";
  }
}
