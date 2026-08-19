package com.bct.ngtpa.apiservice.application.usecase.session.authflow;

import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public record ValidateAuthFlowSessionCsrfCommand(
    AuthFlowSessionId authFlowSessionId,
    String submittedToken,
    Instant now,
    String origin,
    String referer,
    Collection<String> allowedOrigins,
    String correlationId) {
  public ValidateAuthFlowSessionCsrfCommand {
    Objects.requireNonNull(authFlowSessionId, "authFlowSessionId must not be null");
    Objects.requireNonNull(now, "now must not be null");
    allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
  }

  @Override
  public String toString() {
    return "ValidateAuthFlowSessionCsrfCommand[authFlowSessionId=<redacted>, submittedToken=<redacted>, now="
        + now
        + ", origin=<redacted>, referer=<redacted>, allowedOriginsSize="
        + allowedOrigins.size()
        + ", correlationId="
        + correlationId
        + "]";
  }
}
