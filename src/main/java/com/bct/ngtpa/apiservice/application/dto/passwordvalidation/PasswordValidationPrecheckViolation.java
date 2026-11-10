package com.bct.ngtpa.apiservice.application.dto.passwordvalidation;

import java.util.List;
import java.util.Objects;

public record PasswordValidationPrecheckViolation(Kind kind, String code, List<String> targets) {

  public PasswordValidationPrecheckViolation {
    Objects.requireNonNull(kind, "kind must not be null");
    Objects.requireNonNull(code, "code must not be null");
    targets = List.copyOf(Objects.requireNonNull(targets, "targets must not be null"));
  }

  public enum Kind {
    FIELD,
    CROSS_FIELD,
    FORM
  }

  @Override
  public String toString() {
    return "PasswordValidationPrecheckViolation[<redacted>]";
  }
}
