package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.resend;

public record ResendForgotPasswordOtpResult(
    ResendForgotPasswordOtpStatus status,
    String maskedDestination,
    Integer expiresInSeconds,
    Integer resendCooldownSeconds,
    Integer remainingResendAttempts,
    Integer retryAfterSeconds) {
  @Override
  public String toString() {
    return "ResendForgotPasswordOtpResult[status="
        + status
        + ", maskedDestination=<redacted>, expiresInSeconds="
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
