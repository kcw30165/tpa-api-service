package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.resend;

public enum ResendForgotPasswordOtpStatus {
  RESENT,
  COMPLETED_REPLAY,
  COOLDOWN_ACTIVE,
  RESEND_LIMIT_REACHED,
  EXPIRED,
  AUTH_FLOW_INVALID,
  SECURITY_REJECTED,
  IDEMPOTENCY_KEY_REUSED,
  PREVIOUS_FAILED,
  DELIVERY_FAILED,
  UNKNOWN_OUTCOME,
  SYSTEM_FAILED
}
