package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency;

import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyAction;

/** Stable shared-idempotency action names for the Forgot Password capability. */
public enum ForgotPasswordIdempotencyAction {
  START("start"),
  DETAILS_AND_OTP("details-and-otp"),
  OTP_RESEND("otp-resend"),
  OTP_VERIFY("otp-verify"),
  COMPLETE("complete");

  private final IdempotencyAction action;

  ForgotPasswordIdempotencyAction(String value) {
    this.action = new IdempotencyAction(value);
  }

  public IdempotencyAction action() {
    return action;
  }
}
