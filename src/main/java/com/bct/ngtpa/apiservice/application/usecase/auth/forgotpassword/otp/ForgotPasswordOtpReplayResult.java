package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp;

public record ForgotPasswordOtpReplayResult(
    Status status,
    String maskedDestination,
    Integer expiresInSeconds,
    Integer remainingResendAttempts,
    Integer retryAfterSeconds) {
  public enum Status {
    ACTIVE,
    EXPIRED,
    SYSTEM_ERROR
  }

  public String nextStep() {
    return status == Status.ACTIVE
        ? "VERIFY_OTP"
        : status == Status.EXPIRED ? "RESEND_OTP" : "NONE";
  }

  @Override
  public String toString() {
    return "ForgotPasswordOtpReplayResult[status="
        + status
        + ",maskedDestination=<redacted>,timing=<redacted>]";
  }
}
