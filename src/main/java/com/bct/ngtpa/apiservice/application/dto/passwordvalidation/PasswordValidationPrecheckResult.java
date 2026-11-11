package com.bct.ngtpa.apiservice.application.dto.passwordvalidation;

import java.util.List;
import java.util.Objects;

public record PasswordValidationPrecheckResult(
    List<PasswordValidationPrecheckViolation> violations, String businessErrorCode) {

  public PasswordValidationPrecheckResult {
    violations = List.copyOf(Objects.requireNonNull(violations, "violations must not be null"));
    if (!violations.isEmpty() && businessErrorCode != null) {
      throw new IllegalArgumentException(
          "Precheck cannot contain both violations and business error");
    }
    if (businessErrorCode != null && businessErrorCode.isBlank()) {
      throw new IllegalArgumentException("businessErrorCode must not be blank");
    }
  }

  public PasswordValidationPrecheckResult(List<PasswordValidationPrecheckViolation> violations) {
    this(violations, null);
  }

  public static PasswordValidationPrecheckResult success() {
    return new PasswordValidationPrecheckResult(List.of(), null);
  }

  public static PasswordValidationPrecheckResult rejected(
      List<PasswordValidationPrecheckViolation> violations) {
    if (violations == null || violations.isEmpty()) {
      throw new IllegalArgumentException("Rejected precheck requires at least one violation");
    }
    return new PasswordValidationPrecheckResult(violations, null);
  }

  public static PasswordValidationPrecheckResult businessRejected(String errorCode) {
    return new PasswordValidationPrecheckResult(
        List.of(), Objects.requireNonNull(errorCode, "errorCode must not be null"));
  }

  public boolean isSuccess() {
    return violations.isEmpty() && businessErrorCode == null;
  }

  public boolean isBusinessRejected() {
    return businessErrorCode != null;
  }

  @Override
  public String toString() {
    return "PasswordValidationPrecheckResult[violations=<redacted>, businessErrorCode="
        + businessErrorCode
        + "]";
  }
}
