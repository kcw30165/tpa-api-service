package com.bct.ngtpa.apiservice.application.usecase.session.authflow;

import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowStatus;
import java.util.Objects;

public record UpdateAuthFlowStatusCommand(
    AuthFlowSessionId authFlowSessionId, AuthFlowStatus newStatus, String correlationId) {
  public UpdateAuthFlowStatusCommand {
    Objects.requireNonNull(authFlowSessionId, "authFlowSessionId must not be null");
    Objects.requireNonNull(newStatus, "newStatus must not be null");
  }

  @Override
  public String toString() {
    return "UpdateAuthFlowStatusCommand[authFlowSessionId=<redacted>, newStatus="
        + newStatus
        + ", correlationId="
        + correlationId
        + "]";
  }
}
