package com.bct.ngtpa.apiservice.application.dto.passwordvalidation;

import java.util.List;
import java.util.Objects;

public record PasswordValidationIssuanceResult(
    Status status,
    String token,
    int expiresInSeconds,
    int retryAfterSeconds,
    List<PasswordValidationPrecheckViolation> violations,
    String errorCode) {

  public PasswordValidationIssuanceResult {
    Objects.requireNonNull(status, "status must not be null");
    violations = List.copyOf(Objects.requireNonNull(violations, "violations must not be null"));
  }

  public enum Status {
    ISSUED,
    INCORRECT,
    LOCKED,
    LOCK_CLEANUP_FAILED,
    THROTTLED,
    AUTHENTICATION_REQUIRED,
    UNAVAILABLE,
    REQUEST_INVALID,
    FEATURE_INVALID,
    FEATURE_UNAVAILABLE,
    FEATURE_BUSINESS_REJECTED
  }

  public static PasswordValidationIssuanceResult of(Status status) {
    return new PasswordValidationIssuanceResult(status, null, 0, 0, List.of(), null);
  }

  public static PasswordValidationIssuanceResult requestInvalid(
      List<PasswordValidationPrecheckViolation> violations) {
    return new PasswordValidationIssuanceResult(
        Status.REQUEST_INVALID, null, 0, 0, violations, null);
  }

  public static PasswordValidationIssuanceResult featureInvalid(
      List<PasswordValidationPrecheckViolation> violations) {
    return new PasswordValidationIssuanceResult(
        Status.FEATURE_INVALID, null, 0, 0, violations, null);
  }

  public static PasswordValidationIssuanceResult featureUnavailable(String errorCode) {
    return new PasswordValidationIssuanceResult(
        Status.FEATURE_UNAVAILABLE, null, 0, 0, List.of(), errorCode);
  }

  public static PasswordValidationIssuanceResult featureBusinessRejected(String errorCode) {
    return new PasswordValidationIssuanceResult(
        Status.FEATURE_BUSINESS_REJECTED, null, 0, 0, List.of(), errorCode);
  }

  @Override
  public String toString() {
    return "PasswordValidationIssuanceResult[status=" + status + ", grant=<redacted>]";
  }
}
