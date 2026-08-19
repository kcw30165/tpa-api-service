package com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword;

import java.time.Instant;
import java.util.Objects;

public record ForgotPasswordResetAuthorization(
    String authorizationId,
    String authFlowSessionId,
    String resetSubjectId,
    Instant issuedAt,
    Instant expiresAt,
    Instant consumedAt) {

  public ForgotPasswordResetAuthorization {
    authorizationId = requireText(authorizationId, "authorizationId");
    authFlowSessionId = requireText(authFlowSessionId, "authFlowSessionId");
    resetSubjectId = requireText(resetSubjectId, "resetSubjectId");
    Objects.requireNonNull(issuedAt, "issuedAt must not be null");
    Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    if (!expiresAt.isAfter(issuedAt)) {
      throw new IllegalArgumentException("expiresAt must be after issuedAt");
    }
  }

  private static String requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value.trim();
  }

  @Override
  public String toString() {
    return "ForgotPasswordResetAuthorization[authorizationId=<redacted>, authFlowSessionId=<redacted>, resetSubjectId=<redacted>, issuedAt="
        + issuedAt
        + ", expiresAt="
        + expiresAt
        + ", consumedAt="
        + consumedAt
        + "]";
  }
}
