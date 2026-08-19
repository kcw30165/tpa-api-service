package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.resend;

import java.time.Instant;

public record ForgotPasswordOtpResendEligibilityResult(
    ForgotPasswordOtpResendEligibilityStatus status,
    Instant latestAcceptedAt,
    Integer retryAfterSeconds,
    int successfulResendCount) {
  @Override
  public String toString() {
    return "ForgotPasswordOtpResendEligibilityResult[status="
        + status
        + ",timing=<redacted>,count=<redacted>]";
  }
}
