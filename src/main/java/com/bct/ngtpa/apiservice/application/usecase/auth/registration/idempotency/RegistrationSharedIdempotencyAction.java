package com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency;

/** Stable shared action names for the four Registration idempotency boundaries. */
public enum RegistrationSharedIdempotencyAction {
  START("start"),
  OTP_RESEND("otp-resend"),
  OTP_VERIFY("otp-verify"),
  COMPLETE("complete");

  private final String value;

  RegistrationSharedIdempotencyAction(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }
}
