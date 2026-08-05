package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.resend;

public record ForgotPasswordOtpResendResult(ForgotPasswordOtpResendEligibilityStatus status, Integer retryAfterSeconds,
        int successfulResendCount) {
    @Override
    public String toString() {
        return "ForgotPasswordOtpResendResult[status=" + status + ",safeState=<redacted>]";
    }
}
