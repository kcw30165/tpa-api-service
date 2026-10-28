package com.bct.ngtpa.apiservice.application.dto.passwordvalidation;

import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import java.util.Objects;

/** Identity must originate from the authenticated server context, never the request body. */
public record PasswordCheckCommand(
    String customerId, String password, ActorUserRole actorUserRole) {
  public PasswordCheckCommand {
    Objects.requireNonNull(customerId, "customerId must not be null");
    Objects.requireNonNull(password, "password must not be null");
    Objects.requireNonNull(actorUserRole, "actorUserRole must not be null");
  }

  @Override
  public String toString() {
    return "PasswordCheckCommand[customerId=<redacted>, password=<redacted>, actorUserRole="
        + actorUserRole + "]";
  }
}
