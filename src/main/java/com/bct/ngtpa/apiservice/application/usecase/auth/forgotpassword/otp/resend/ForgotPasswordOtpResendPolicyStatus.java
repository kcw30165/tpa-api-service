package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.resend;

public enum ForgotPasswordOtpResendPolicyStatus {
  ALLOWED,
  COOLDOWN_ACTIVE,
  RESEND_LIMIT_REACHED
}
