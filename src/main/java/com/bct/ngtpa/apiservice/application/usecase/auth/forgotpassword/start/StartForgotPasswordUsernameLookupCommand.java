package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.start;

import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import java.util.Objects;

public record StartForgotPasswordUsernameLookupCommand(
    String normalizedUsername, String correlationId, ActorUserRole actorUserRole) {
  public StartForgotPasswordUsernameLookupCommand {
    if (normalizedUsername == null || normalizedUsername.isBlank()) {
      throw new IllegalArgumentException("normalizedUsername must not be blank");
    }
    Objects.requireNonNull(correlationId, "correlationId must not be null");
    Objects.requireNonNull(actorUserRole, "actorUserRole must not be null");
    normalizedUsername = normalizedUsername.trim();
  }

  public StartForgotPasswordUsernameLookupCommand(String normalizedUsername, String correlationId) {
    this(normalizedUsername, correlationId, ActorUserRole.ROLE_USER_MBR);
  }

  @Override
  public String toString() {
    return "StartForgotPasswordUsernameLookupCommand[normalizedUsername=<redacted>, correlationId="
        + correlationId
        + "]";
  }
}
