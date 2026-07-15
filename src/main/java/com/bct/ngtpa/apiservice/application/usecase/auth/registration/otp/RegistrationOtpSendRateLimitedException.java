package com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp;

/** Internal signal that the configured destination-scoped OTP send limit blocked delivery. */
public final class RegistrationOtpSendRateLimitedException extends RuntimeException {
    private final int retryAfterSeconds;
    public RegistrationOtpSendRateLimitedException(int retryAfterSeconds) {
        super("Registration OTP send rate limited");
        if (retryAfterSeconds <= 0) throw new IllegalArgumentException("retryAfterSeconds must be positive");
        this.retryAfterSeconds = retryAfterSeconds;
    }
    public int retryAfterSeconds() { return retryAfterSeconds; }
}
