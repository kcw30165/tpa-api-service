package com.bct.ngtpa.apiservice.adapter.in.web.auth.login.dto;

public record LoginOtpSendDataResponse(
    String maskedDestination,
    Integer expiresInSeconds,
    Integer resendCooldownSeconds,
    Integer retryAfterSeconds) {
  @Override
  public String toString() {
    return "LoginOtpSendDataResponse[maskedDestination=<redacted>, expiresInSeconds="
        + expiresInSeconds
        + ", resendCooldownSeconds="
        + resendCooldownSeconds
        + ", retryAfterSeconds="
        + retryAfterSeconds
        + "]";
  }
}
