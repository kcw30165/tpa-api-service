package com.bct.ngtpa.apiservice.application.usecase.auth.registration.cancel;

import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import java.util.Objects;

public record CancelRegistrationCommand(
    AuthFlowSessionId authFlowSessionId,
    String csrfToken,
    String origin,
    String referer,
    String correlationId) {

  public CancelRegistrationCommand {
    Objects.requireNonNull(authFlowSessionId, "authFlowSessionId must not be null");
  }

  @Override
  public String toString() {
    return "CancelRegistrationCommand[authFlowSessionId=<redacted>, csrfToken=<redacted>, "
        + "origin=<redacted>, referer=<redacted>, correlationId="
        + (correlationId == null ? "" : "<redacted>")
        + "]";
  }
}
