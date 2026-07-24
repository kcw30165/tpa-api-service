package com.bct.ngtpa.apiservice.application.dto.auth.login.otp.resend;

public record ResendLoginOtpResult(ResendLoginOtpStatus status, String maskedDestination, Integer expiresInSeconds,
        Integer resendCooldownSeconds, Integer retryAfterSeconds) {
    @Override
    public String toString() {
        return "ResendLoginOtpResult[status=" + status + ", maskedDestination=<redacted>, expiresInSeconds="
                + expiresInSeconds + ", resendCooldownSeconds=" + resendCooldownSeconds + ", retryAfterSeconds="
                + retryAfterSeconds + "]";
    }
}
