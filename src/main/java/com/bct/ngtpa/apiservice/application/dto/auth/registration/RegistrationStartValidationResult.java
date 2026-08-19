package com.bct.ngtpa.apiservice.application.dto.auth.registration;

import java.util.List;
import java.util.Objects;

public record RegistrationStartValidationResult(
    boolean valid,
    RegistrationStartCommand command,
    List<RegistrationStartValidationError> errors) {
  public RegistrationStartValidationResult {
    errors = errors == null ? List.of() : List.copyOf(errors);
    if (valid) {
      Objects.requireNonNull(command, "command must not be null when valid");
      if (!errors.isEmpty()) {
        throw new IllegalArgumentException("valid result must not contain errors");
      }
    }
    if (!valid && errors.isEmpty()) {
      throw new IllegalArgumentException("invalid result requires errors");
    }
  }

  public static RegistrationStartValidationResult valid(RegistrationStartCommand command) {
    return new RegistrationStartValidationResult(true, command, List.of());
  }

  public static RegistrationStartValidationResult invalid(
      List<RegistrationStartValidationError> errors) {
    return new RegistrationStartValidationResult(false, null, errors);
  }

  @Override
  public String toString() {
    return "RegistrationStartValidationResult[valid="
        + valid
        + ", command=<redacted>, errorCount="
        + errors.size()
        + "]";
  }
}
