package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.resend;

public record ResendForgotPasswordOtpResult(
    ResendForgotPasswordOtpStatus status,
    String maskedDestination,
    Integer expiresInSeconds,
    Integer resendCooldownSeconds,
    Integer remainingResendAttempts,
    Integer retryAfterSeconds,
    ForgotPasswordOtpResendPublicationReceipt publicationReceipt) {
  public ResendForgotPasswordOtpResult(
      ResendForgotPasswordOtpStatus status,
      String maskedDestination,
      Integer expiresInSeconds,
      Integer resendCooldownSeconds,
      Integer remainingResendAttempts,
      Integer retryAfterSeconds) {
    this(
        status,
        maskedDestination,
        expiresInSeconds,
        resendCooldownSeconds,
        remainingResendAttempts,
        retryAfterSeconds,
        null);
  }

  public ResendForgotPasswordOtpResult withPublicationReceipt(
      ForgotPasswordOtpResendPublicationReceipt receipt) {
    return new ResendForgotPasswordOtpResult(
        status,
        maskedDestination,
        expiresInSeconds,
        resendCooldownSeconds,
        remainingResendAttempts,
        retryAfterSeconds,
        receipt);
  }

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
