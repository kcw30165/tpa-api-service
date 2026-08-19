package com.bct.ngtpa.apiservice.application.service.auth.registration.context;

public enum RegistrationProtectedAuthFlowContextStatus {
  VALID,
  MISSING_SESSION,
  EXPIRED_OR_TERMINAL_SESSION,
  WRONG_SCENARIO,
  INVALID_STATUS,
  MISSING_ACTIVE_OTP,
  MISSING_CONTEXT,
  CONTEXT_DECRYPTION_FAILED,
  CSRF_INVALID
}
