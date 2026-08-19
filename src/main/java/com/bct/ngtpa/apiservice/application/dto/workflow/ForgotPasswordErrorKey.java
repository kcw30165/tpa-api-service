package com.bct.ngtpa.apiservice.application.dto.workflow;

import java.util.Objects;

public enum ForgotPasswordErrorKey {
  AUTH_FLOW_INVALID("err.forgot-password.authFlow.invalid"),
  CSRF_INVALID("err.forgot-password.csrf.invalid"),
  SECURITY_INVALID("err.forgot-password.security.invalid"),

  OTP_INVALID("err.forgot-password.otp.invalid"),
  OTP_EXPIRED("err.forgot-password.otp.expired"),
  OTP_MAX_ATTEMPTS_REACHED("err.forgot-password.otp.maxAttemptsReached"),

  OTP_RESEND_COOLDOWN("err.forgot-password.otp.resend.cooldown"),
  OTP_RESEND_LIMIT_REACHED("err.forgot-password.otp.resend.limitReached"),
  OTP_RESEND_PREVIOUS_FAILED("err.forgot-password.otp.resend.previousFailed"),

  SYSTEM_UNAVAILABLE("err.system.unavailable");

  private final String value;

  ForgotPasswordErrorKey(String value) {
    this.value = Objects.requireNonNull(value, "value must not be null");

    if (value.isBlank()) {
      throw new IllegalArgumentException("Forgot Password error-key value must not be blank");
    }
  }

  public String value() {
    return value;
  }
}
