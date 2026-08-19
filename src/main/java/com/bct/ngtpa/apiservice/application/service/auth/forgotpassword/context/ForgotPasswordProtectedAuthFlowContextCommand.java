package com.bct.ngtpa.apiservice.application.service.auth.forgotpassword.context;

import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowStatus;
import java.util.Objects;
import java.util.Set;

public record ForgotPasswordProtectedAuthFlowContextCommand(
    AuthFlowSessionId authFlowSessionId,
    String csrfToken,
    String origin,
    String referer,
    String correlationId,
    Set<AuthFlowStatus> acceptedStatuses,
    boolean csrfRequired) {

  public ForgotPasswordProtectedAuthFlowContextCommand {
    Objects.requireNonNull(authFlowSessionId, "authFlowSessionId must not be null");
    if (csrfRequired && (csrfToken == null || csrfToken.isBlank())) {
      throw new IllegalArgumentException("csrfToken must not be blank when csrfRequired is true");
    }
    acceptedStatuses =
        acceptedStatuses == null || acceptedStatuses.isEmpty()
            ? Set.of(AuthFlowStatus.IDENTITY_VERIFIED)
            : Set.copyOf(acceptedStatuses);
  }

  @Override
  public String toString() {
    return "ForgotPasswordProtectedAuthFlowContextCommand[authFlowSessionId=<redacted>, csrfToken=<redacted>, origin=<redacted>, referer=<redacted>, correlationId="
        + (correlationId == null ? "" : "<redacted>")
        + ", acceptedStatuses="
        + acceptedStatuses
        + ", csrfRequired="
        + csrfRequired
        + "]";
  }
}
