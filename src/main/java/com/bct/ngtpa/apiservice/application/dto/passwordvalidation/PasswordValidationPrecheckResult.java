package com.bct.ngtpa.apiservice.application.dto.passwordvalidation;

import java.util.List;
import java.util.Objects;

public record PasswordValidationPrecheckResult(
    List<PasswordValidationPrecheckViolation> violations) {

  public PasswordValidationPrecheckResult {
    violations = List.copyOf(Objects.requireNonNull(violations, "violations must not be null"));
  }

  public static PasswordValidationPrecheckResult success() {
    return new PasswordValidationPrecheckResult(List.of());
  }

  public static PasswordValidationPrecheckResult rejected(
      List<PasswordValidationPrecheckViolation> violations) {
    if (violations == null || violations.isEmpty()) {
      throw new IllegalArgumentException("Rejected precheck requires at least one violation");
    }
    return new PasswordValidationPrecheckResult(violations);
  }

  public boolean isSuccess() {
    return violations.isEmpty();
  }

  @Override
  public String toString() {
    return "PasswordValidationPrecheckResult[violations=<redacted>]";
  }
}
