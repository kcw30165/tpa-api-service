package com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context;

public enum ForgotPasswordProtectedPhase {
  USERNAME_VERIFIED,
  IDENTITY_VERIFIED,
  OTP_SENT,
  OTP_DELIVERY_UNKNOWN,
  PASSWORD_SETUP
}
