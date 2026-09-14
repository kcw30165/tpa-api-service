package com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency;

/** Protected Start continuity. Values are stored outside the shared idempotency record. */
public record RegistrationStartProtectedReplayState(String csrfToken, String authFlowSessionId) {
  public RegistrationStartProtectedReplayState {
    if (csrfToken == null || csrfToken.isBlank()) {
      throw new IllegalArgumentException("registration Start replay CSRF token must not be blank");
    }
    if (authFlowSessionId == null || authFlowSessionId.isBlank()) {
      throw new IllegalArgumentException("registration Start replay session must not be blank");
    }
  }

  @Override
  public String toString() {
    return "RegistrationStartProtectedReplayState[csrfToken=<redacted>, authFlowSessionId=<redacted>]";
  }
}
