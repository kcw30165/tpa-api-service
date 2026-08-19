package com.bct.ngtpa.apiservice.application.dto.auth.idempotency;

public enum RegistrationIdempotencyAction {
  REGISTRATION_START("registration-start"),
  OTP_RESEND("otp-resend"),
  OTP_VERIFY("otp-verify"),
  REGISTRATION_COMPLETE("registration-complete");

  private final String keyNamespace;

  RegistrationIdempotencyAction(String keyNamespace) {
    this.keyNamespace = keyNamespace;
  }

  public String keyNamespace() {
    return keyNamespace;
  }
}
