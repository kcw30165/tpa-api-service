package com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency;

public record ForgotPasswordRequestHash(String value) {
  public ForgotPasswordRequestHash {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("ForgotPasswordRequestHash must not be blank");
    }
    value = value.trim();
  }

  @Override
  public String toString() {
    return "ForgotPasswordRequestHash[value=<redacted>]";
  }
}
