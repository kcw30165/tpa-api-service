package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.shared;

import java.time.Instant;

/** Protected Forgot Password Start replay continuity stored outside shared idempotency. */
public record ForgotPasswordStartProtectedReplayState(
    String authFlowSessionId, String identityType, Instant expiresAt) {
  public ForgotPasswordStartProtectedReplayState {
    authFlowSessionId = requireText(authFlowSessionId, "auth-flow session id");
    identityType = requireText(identityType, "identity type");
    if (expiresAt == null) {
      throw new IllegalArgumentException("Forgot Password Start replay expiry must not be null");
    }
  }

  @Override
  public String toString() {
    return "ForgotPasswordStartProtectedReplayState[authFlowSessionId=<redacted>, "
        + "identityType=<redacted>, expiresAt="
        + expiresAt
        + "]";
  }

  private static String requireText(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Forgot Password Start " + label + " must not be blank");
    }
    return value.trim();
  }
}
