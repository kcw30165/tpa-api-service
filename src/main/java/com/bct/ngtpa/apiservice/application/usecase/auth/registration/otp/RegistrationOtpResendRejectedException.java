package com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp;

public class RegistrationOtpResendRejectedException extends RuntimeException {
    private final ResendRegistrationOtpResultStatus status;
    private final Integer retryAfterSeconds;
    private final Integer remainingResendAttempts;

    public RegistrationOtpResendRejectedException(
            ResendRegistrationOtpResultStatus status,
            Integer retryAfterSeconds,
            Integer remainingResendAttempts) {
        super("Registration OTP resend rejected: " + status);
        this.status = status;
        this.retryAfterSeconds = retryAfterSeconds;
        this.remainingResendAttempts = remainingResendAttempts;
    }

    public ResendRegistrationOtpResultStatus status() {
        return status;
    }

    public Integer retryAfterSeconds() {
        return retryAfterSeconds;
    }

    public Integer remainingResendAttempts() {
        return remainingResendAttempts;
    }

    @Override
    public String toString() {
        return "RegistrationOtpResendRejectedException[status=" + status
                + ", retryAfterSeconds=" + retryAfterSeconds
                + ", remainingResendAttempts=" + remainingResendAttempts + "]";
    }
}
