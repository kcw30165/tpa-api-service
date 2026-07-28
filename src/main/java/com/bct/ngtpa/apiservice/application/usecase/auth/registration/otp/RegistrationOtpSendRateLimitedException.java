package com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp;

/**
 * Internal signal that the configured destination-scoped OTP send limit blocked
 * delivery.
 */
public final class RegistrationOtpSendRateLimitedException extends RuntimeException {
    private final int retryAfterSeconds;
    private final ResendRegistrationOtpResultStatus status;

    public RegistrationOtpSendRateLimitedException() {
        super("Registration OTP send rate limited");
        this.retryAfterSeconds = 0;
        this.status = ResendRegistrationOtpResultStatus.SEND_RATE_LIMITED;
    }

    public int retryAfterSeconds() {
        return retryAfterSeconds;
    }

    public ResendRegistrationOtpResultStatus status() {
        return status;
    }
}
