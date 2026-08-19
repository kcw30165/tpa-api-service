package com.bct.ngtpa.apiservice.application.usecase.session.authflow;

import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import java.util.Objects;

public record CancelAuthFlowSessionCommand(
    AuthFlowSessionId authFlowSessionId, String reason, String correlationId) {
  public CancelAuthFlowSessionCommand {
    Objects.requireNonNull(authFlowSessionId, "authFlowSessionId must not be null");
  }

  @Override
  public String toString() {
    return "CancelAuthFlowSessionCommand[authFlowSessionId=<redacted>, reason="
        + reason
        + ", correlationId="
        + correlationId
        + "]";
  }
}
