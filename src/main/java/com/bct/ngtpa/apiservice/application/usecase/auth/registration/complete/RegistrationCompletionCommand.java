package com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete;

import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import java.util.Objects;

public record RegistrationCompletionCommand(
    String accountEnv,
    String trustCode,
    String customerId,
    String userId,
    String password,
    String dateOfBirth,
    String mobileNumber,
    String email,
    String correlationId,
    ActorUserRole actorUserRole) {
  public RegistrationCompletionCommand {
    accountEnv = required(accountEnv, "accountEnv");
    trustCode = required(trustCode, "trustCode");
    customerId = required(customerId, "customerId");
    userId = required(userId, "userId");
    password = required(password, "password");
    dateOfBirth = required(dateOfBirth, "dateOfBirth");
    mobileNumber = mobileNumber == null ? "" : mobileNumber;
    email = email == null ? "" : email;
    Objects.requireNonNull(actorUserRole, "actorUserRole must not be null");
  }

  private static String required(String value, String name) {
    Objects.requireNonNull(value, name + " must not be null");
    if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    return value;
  }

  @Override
  public String toString() {
    return "RegistrationCompletionCommand[accountEnv=<redacted>, trustCode=<redacted>, customerId=<redacted>, "
        + "userId=<redacted>, password=<redacted>, dateOfBirth=<redacted>, mobileNumber=<redacted>, "
        + "email=<redacted>, correlationId="
        + correlationId
        + ", actorUserRole="
        + actorUserRole
        + "]";
  }
}
