package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.verify;

import java.util.Objects;

/** Result of the acquired OTP_VERIFY protected operation. */
public record ForgotPasswordOtpVerifyProtectedExecutionResult(
    VerifyForgotPasswordOtpResult result, ForgotPasswordOtpVerifyIncident incident) {
  public ForgotPasswordOtpVerifyProtectedExecutionResult {
    Objects.requireNonNull(result, "result must not be null");
    if ((result.status() == VerifyForgotPasswordOtpStatus.SYSTEM_FAILED) != (incident != null)) {
      throw new IllegalArgumentException(
          "OTP_VERIFY system failure must have exactly one incident classification");
    }
  }
}
