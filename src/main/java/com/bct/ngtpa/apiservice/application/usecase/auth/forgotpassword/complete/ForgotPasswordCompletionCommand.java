package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.complete;

import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import java.util.Objects;

public record ForgotPasswordCompletionCommand(
    String customerId, String password, String correlationId, ActorUserRole actorUserRole) {
  public ForgotPasswordCompletionCommand {
    customerId = required(customerId, "customerId");
    password = required(password, "password");
    correlationId = correlationId == null || correlationId.isBlank() ? null : correlationId.trim();
    Objects.requireNonNull(actorUserRole, "actorUserRole must not be null");
  }

  private static String required(String value, String name) {
    Objects.requireNonNull(value, name + " must not be null");
    if (value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return value;
  }

  @Override
  public String toString() {
    return "ForgotPasswordCompletionCommand[customerId=<redacted>, ****** correlationId="
        + correlationId
        + "]";
  }
}
