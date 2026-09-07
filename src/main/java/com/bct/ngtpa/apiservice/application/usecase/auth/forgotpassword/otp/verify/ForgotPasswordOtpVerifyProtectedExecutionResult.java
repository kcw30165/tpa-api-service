package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.verify;

import java.util.Objects;

/** Result of the acquired OTP_VERIFY protected operation. */
public record ForgotPasswordOtpVerifyProtectedExecutionResult(
    VerifyForgotPasswordOtpResult result) {
  public ForgotPasswordOtpVerifyProtectedExecutionResult {
    Objects.requireNonNull(result, "result must not be null");
  }
}
