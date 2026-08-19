package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.details;

import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import java.util.Objects;

public record ForgotPasswordDetailsLookupCommand(
    String searchBy,
    String searchValue,
    String dateOfBirth,
    String email,
    String mobile,
    String customerId,
    String requestId,
    ActorUserRole actorUserRole) {
  public ForgotPasswordDetailsLookupCommand {
    Objects.requireNonNull(actorUserRole, "actorUserRole must not be null");
  }

  @Override
  public String toString() {
    return "ForgotPasswordDetailsLookupCommand[searchBy="
        + searchBy
        + ", searchValue=<redacted>, dateOfBirth=<redacted>, email=<redacted>, mobile=<redacted>, customerId=<redacted>, requestId="
        + requestId
        + ", actorUserRole="
        + actorUserRole
        + "]";
  }
}
