package com.bct.ngtpa.apiservice.adapter.in.web.passwordvalidation;

public record PasswordValidationResult(Data data) {
  public record Data(String token, Integer expiresInSeconds) {
    @Override
    public String toString() {
      return "Data[token=<redacted>, expiresInSeconds=" + expiresInSeconds + "]";
    }
  }
}
