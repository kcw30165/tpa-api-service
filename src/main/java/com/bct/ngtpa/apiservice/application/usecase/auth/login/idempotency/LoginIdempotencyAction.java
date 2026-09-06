package com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency;

import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyAction;

/** Stable shared-idempotency action names for the Login capability. */
public enum LoginIdempotencyAction {
  CREDENTIALS("credentials"),
  OTP_SEND("otp-send"),
  OTP_RESEND("otp-resend"),
  OTP_VERIFY("otp-verify");

  private final IdempotencyAction action;

  LoginIdempotencyAction(String value) {
    this.action = new IdempotencyAction(value);
  }

  public IdempotencyAction action() {
    return action;
  }
}
