package com.bct.ngtpa.apiservice.application.dto.auth.login.idempotency;

public record LoginOtpSendReplaySnapshot(String maskedDestination, int expiresInSeconds, int resendCooldownSeconds) {
    public LoginOtpSendReplaySnapshot {
        if (maskedDestination == null || maskedDestination.isBlank() || expiresInSeconds <= 0
                || resendCooldownSeconds < 0)
            throw new IllegalArgumentException("Invalid safe OTP replay snapshot");
    }

    @Override
    public String toString() {
        return "LoginOtpSendReplaySnapshot[maskedDestination=<redacted>, expiresInSeconds=" + expiresInSeconds
                + ", resendCooldownSeconds=" + resendCooldownSeconds + "]";
    }
}
