package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.resend;

public record ResendForgotPasswordOtpResult(
        ResendForgotPasswordOtpStatus status,
        String maskedDestination,
        Integer expiresInSeconds,
        Integer resendAvailableInSeconds) {
    @Override
    public String toString() {
        return "ResendForgotPasswordOtpResult[status=" + status
                + ", maskedDestination=<redacted>, expiresInSeconds=" + expiresInSeconds
                + ", resendAvailableInSeconds=" + resendAvailableInSeconds + "]";
    }
}