package com.bct.ngtpa.apiservice.application.usecase.auth.login.otp;

import java.util.Objects;

public record SendLoginOtpResult(SendLoginOtpStatus status, String maskedDestination, Integer expiresInSeconds,
        Integer resendCooldownSeconds, Integer retryAfterSeconds) {
    public SendLoginOtpResult {
        Objects.requireNonNull(status);
        if (status == SendLoginOtpStatus.SENT || status == SendLoginOtpStatus.COMPLETED_REPLAY) {
            if (maskedDestination == null || maskedDestination.isBlank())
                throw new IllegalArgumentException("successful send requires maskedDestination");
            if (expiresInSeconds == null || expiresInSeconds <= 0)
                throw new IllegalArgumentException("successful send requires positive expiresInSeconds");
        }
    }

    @Override
    public String toString() {
        return "SendLoginOtpResult[status=" + status + ", maskedDestination=<redacted>, expiresInSeconds="
                + expiresInSeconds + ", resendCooldownSeconds=" + resendCooldownSeconds + ", retryAfterSeconds="
                + retryAfterSeconds + "]";
    }
}
