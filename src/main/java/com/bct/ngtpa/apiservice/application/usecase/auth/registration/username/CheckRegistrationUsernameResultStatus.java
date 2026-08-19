package com.bct.ngtpa.apiservice.application.usecase.auth.registration.username;

public enum CheckRegistrationUsernameResultStatus {
  USERNAME_AVAILABLE,
  USERNAME_UNAVAILABLE,
  VALIDATION_FAILED,
  INVALID_AUTH_FLOW,
  CSRF_INVALID,
  INVALID_STATE,
  DOWNSTREAM_REJECTED,
  DOWNSTREAM_FAILED,
  SYSTEM_FAILED
}
