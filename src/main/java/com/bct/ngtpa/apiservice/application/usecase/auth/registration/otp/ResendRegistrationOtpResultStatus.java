package com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp;

public enum ResendRegistrationOtpResultStatus {
    RESENT,
    VALIDATION_FAILED,
    INVALID_AUTH_FLOW,
    CSRF_INVALID,
    DUPLICATE_IN_PROGRESS,
    COMPLETED_REPLAY,
    IDEMPOTENCY_KEY_REUSED,
    COOLDOWN_ACTIVE,
    SEND_RATE_LIMITED,
    MAX_RESEND_ATTEMPTS_REACHED,
    MESSAGE_DELIVERY_FAILED,
    SYSTEM_FAILED
}
