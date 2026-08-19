package com.bct.ngtpa.apiservice.application.dto.auth.login.otp.verify;

public record LoginOtpVerificationOperationReference(String value) {
  public LoginOtpVerificationOperationReference {
    if (value == null || !value.matches("[0-9a-f]{32,}"))
      throw new IllegalArgumentException("invalid verification operation reference");
  }

  @Override
  public String toString() {
    return "LoginOtpVerificationOperationReference[<redacted>]";
  }
}
