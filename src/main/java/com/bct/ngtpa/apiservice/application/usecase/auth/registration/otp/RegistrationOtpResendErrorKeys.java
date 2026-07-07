package com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp;

public final class RegistrationOtpResendErrorKeys {
    public static final String REGISTRATION_AUTH_FLOW_INVALID = "registration.authFlow.invalid";
    public static final String REGISTRATION_CSRF_INVALID = "registration.csrf.invalid";
    public static final String REGISTRATION_OTP_COOLDOWN_ACTIVE = "registration.otp.cooldownActive";
    public static final String REGISTRATION_OTP_MAX_ATTEMPTS_REACHED = "registration.otp.maxAttemptsReached";
    public static final String REGISTRATION_OTP_RESEND_FAILED = "registration.otp.resendFailed";
    public static final String WORKFLOW_IDEMPOTENCY_KEY_REUSED = "workflow.idempotency.keyReused";
    public static final String REGISTRATION_MESSAGE_DELIVERY_FAILED = "registration.messageDelivery.failed";

    private RegistrationOtpResendErrorKeys() {
    }
}
