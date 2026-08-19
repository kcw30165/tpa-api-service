package com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency;

public enum ForgotPasswordIdempotencyAction {
  START("forgot-password-start"),
  DETAILS_AND_OTP("forgot-password-details-and-otp"),
  OTP_RESEND("forgot-password-otp-resend"),
  OTP_VERIFY("forgot-password-otp-verify"),
  COMPLETE("forgot-password-complete"),
  CANCEL("forgot-password-cancel");

  private final String keyNamespace;

  ForgotPasswordIdempotencyAction(String keyNamespace) {
    this.keyNamespace = keyNamespace;
  }

  public String keyNamespace() {
    return keyNamespace;
  }
}
