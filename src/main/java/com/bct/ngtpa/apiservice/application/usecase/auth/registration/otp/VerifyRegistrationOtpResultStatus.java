package com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp;

public enum VerifyRegistrationOtpResultStatus {
    VERIFIED,
    VALIDATION_FAILED,
    INVALID_AUTH_FLOW,
    CSRF_INVALID,
    DUPLICATE_IN_PROGRESS,
    COMPLETED_REPLAY,
    IDEMPOTENCY_KEY_REUSED,
    OTP_INVALID,
    OTP_EXPIRED,
    OTP_LOCKED,
    OTP_ALREADY_VERIFIED,
    TRUST_LOOKUP_FAILED,
    NO_ELIGIBLE_TRUSTS,
    SYSTEM_FAILED
}
