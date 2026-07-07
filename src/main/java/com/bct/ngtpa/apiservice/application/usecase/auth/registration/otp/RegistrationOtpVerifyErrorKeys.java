package com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp;

public final class RegistrationOtpVerifyErrorKeys {
    public static final String REGISTRATION_AUTH_FLOW_INVALID = "registration.authFlow.invalid";
    public static final String REGISTRATION_CSRF_INVALID = "registration.csrf.invalid";
    public static final String REGISTRATION_OTP_INVALID = "registration.otp.invalid";
    public static final String REGISTRATION_OTP_EXPIRED = "registration.otp.expired";
    public static final String REGISTRATION_OTP_MAX_ATTEMPTS_REACHED = "registration.otp.maxAttemptsReached";
    public static final String REGISTRATION_TRUST_LOOKUP_FAILED = "registration.trustLookup.failed";
    public static final String REGISTRATION_NO_ELIGIBLE_TRUSTS = "registration.trustLookup.noEligibleTrusts";
    public static final String WORKFLOW_IDEMPOTENCY_KEY_REUSED = "workflow.idempotency.keyReused";
    private RegistrationOtpVerifyErrorKeys() {}
}
