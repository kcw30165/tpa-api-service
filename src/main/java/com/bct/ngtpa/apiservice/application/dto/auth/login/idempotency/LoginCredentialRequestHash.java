package com.bct.ngtpa.apiservice.application.dto.auth.login.idempotency;

public record LoginCredentialRequestHash(String value) {
  public LoginCredentialRequestHash {
    if (value == null || !value.matches("^[a-f0-9]{64}$"))
      throw new IllegalArgumentException("Invalid login request hash");
  }

  @Override
  public String toString() {
    return "LoginCredentialRequestHash[redacted]";
  }
}
