package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp;

public record ForgotPasswordInitialOtpPolicy(
    int length,
    boolean numericOnly,
    int ttlSeconds,
    int resendCooldownSeconds,
    int maxVerifyAttempts,
    int maxResendAttempts,
    boolean invalidatePreviousOnResend) {
  public ForgotPasswordInitialOtpPolicy {
    if (length <= 0
        || ttlSeconds <= 0
        || resendCooldownSeconds < 0
        || maxVerifyAttempts <= 0
        || maxResendAttempts <= 0
        || !numericOnly
        || !invalidatePreviousOnResend) {
      throw new IllegalArgumentException("Invalid Forgot Password OTP policy");
    }
  }

  public static ForgotPasswordInitialOtpPolicy approved() {
    return new ForgotPasswordInitialOtpPolicy(6, true, 300, 60, 5, 5, true);
  }
}
