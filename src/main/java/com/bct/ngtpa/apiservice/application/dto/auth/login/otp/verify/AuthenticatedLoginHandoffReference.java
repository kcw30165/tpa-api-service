package com.bct.ngtpa.apiservice.application.dto.auth.login.otp.verify;

public record AuthenticatedLoginHandoffReference(String value) {
  public AuthenticatedLoginHandoffReference {
    if (value == null || !value.matches("[0-9a-f]{32,}"))
      throw new IllegalArgumentException("invalid handoff reference");
  }

  @Override
  public String toString() {
    return "AuthenticatedLoginHandoffReference[<redacted>]";
  }
}
