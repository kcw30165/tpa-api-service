package com.bct.ngtpa.apiservice.application.usecase.session.authflow;

import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import java.util.Objects;

public record UpdateAuthFlowDestinationCommand(
    AuthFlowSessionId authFlowSessionId,
    String contactType,
    String maskedDestination,
    String correlationId) {
  public UpdateAuthFlowDestinationCommand {
    Objects.requireNonNull(authFlowSessionId, "authFlowSessionId must not be null");
    if (contactType == null || contactType.isBlank())
      throw new IllegalArgumentException("contactType must not be blank");
    if (maskedDestination == null || maskedDestination.isBlank())
      throw new IllegalArgumentException("maskedDestination must not be blank");
    if (maskedDestination.contains("@") && !maskedDestination.contains("*"))
      throw new IllegalArgumentException("maskedDestination must be masked");
  }

  @Override
  public String toString() {
    return "UpdateAuthFlowDestinationCommand[authFlowSessionId=<redacted>, contactType="
        + contactType
        + ", maskedDestination=<redacted>, correlationId="
        + correlationId
        + "]";
  }
}
