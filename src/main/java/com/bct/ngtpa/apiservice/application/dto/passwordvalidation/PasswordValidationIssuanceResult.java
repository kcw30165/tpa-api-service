package com.bct.ngtpa.apiservice.application.dto.passwordvalidation;

public record PasswordValidationIssuanceResult(
    Status status, String token, int expiresInSeconds, int retryAfterSeconds) {
  public enum Status {
    ISSUED,
    INCORRECT,
    LOCKED,
    LOCK_CLEANUP_FAILED,
    THROTTLED,
    AUTHENTICATION_REQUIRED,
    UNAVAILABLE
  }

  public static PasswordValidationIssuanceResult of(Status status) {
    return new PasswordValidationIssuanceResult(status, null, 0, 0);
  }

  @Override
  public String toString() {
    return "PasswordValidationIssuanceResult[status=" + status + ", grant=<redacted>]";
  }
}
