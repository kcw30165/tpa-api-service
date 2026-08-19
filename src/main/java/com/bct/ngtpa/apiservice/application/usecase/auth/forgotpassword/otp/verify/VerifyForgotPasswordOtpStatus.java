package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.verify;

public enum VerifyForgotPasswordOtpStatus {
  VERIFIED,
  COMPLETED_REPLAY,
  OTP_INVALID,
  OTP_EXPIRED,
  OTP_LOCKED,
  AUTH_FLOW_INVALID,
  CSRF_INVALID,
  DUPLICATE_IN_PROGRESS,
  IDEMPOTENCY_KEY_REUSED,
  SYSTEM_FAILED
}
