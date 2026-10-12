package com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.dto;

public record RegistrationOtpResendResponse(
    String maskedDestination,
    Integer expiresInSeconds,
    Integer resendCooldownSeconds,
    Integer remainingResendAttempts,
    Integer retryAfterSeconds) {
  @Override
  public String toString() {
    return "RegistrationOtpResendResponse[maskedDestination="
        + maskedDestination
        + ", expiresInSeconds="
        + expiresInSeconds
        + ", resendCooldownSeconds="
        + resendCooldownSeconds
        + ", remainingResendAttempts="
        + remainingResendAttempts
        + ", retryAfterSeconds="
        + retryAfterSeconds
        + "]";
  }
}
