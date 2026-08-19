package com.bct.ngtpa.apiservice.application.service.auth.registration.context;

import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowStatus;
import java.util.Objects;
import java.util.Set;

public record RegistrationProtectedAuthFlowContextCommand(
    AuthFlowSessionId authFlowSessionId,
    String csrfToken,
    String origin,
    String referer,
    String correlationId,
    Set<AuthFlowStatus> acceptedStatuses) {

  public RegistrationProtectedAuthFlowContextCommand {
    Objects.requireNonNull(authFlowSessionId, "authFlowSessionId must not be null");
    if (csrfToken == null || csrfToken.isBlank()) {
      throw new IllegalArgumentException("csrfToken must not be blank");
    }
    acceptedStatuses =
        acceptedStatuses == null || acceptedStatuses.isEmpty()
            ? Set.of(AuthFlowStatus.OTP_SENT)
            : Set.copyOf(acceptedStatuses);
  }

  /** Existing OTP resend/verify callers remain restricted to OTP_SENT. */
  public RegistrationProtectedAuthFlowContextCommand(
      AuthFlowSessionId authFlowSessionId,
      String csrfToken,
      String origin,
      String referer,
      String correlationId) {
    this(
        authFlowSessionId,
        csrfToken,
        origin,
        referer,
        correlationId,
        Set.of(AuthFlowStatus.OTP_SENT));
  }

  @Override
  public String toString() {
    return "RegistrationProtectedAuthFlowContextCommand[authFlowSessionId=<redacted>, "
        + "csrfToken=<redacted>, origin=<redacted>, referer=<redacted>, correlationId="
        + (correlationId == null ? "" : "<redacted>")
        + ", acceptedStatuses="
        + acceptedStatuses
        + "]";
  }
}
