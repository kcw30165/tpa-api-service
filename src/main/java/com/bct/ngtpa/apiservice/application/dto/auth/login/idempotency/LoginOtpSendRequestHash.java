package com.bct.ngtpa.apiservice.application.dto.auth.login.idempotency;

public record LoginOtpSendRequestHash(String value) {
  public LoginOtpSendRequestHash {
    if (value == null || !value.matches("^[a-f0-9]{64}$"))
      throw new IllegalArgumentException("Invalid OTP send request hash");
  }

  @Override
  public String toString() {
    return "LoginOtpSendRequestHash[redacted]";
  }
}
