package com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp;

public final class RegistrationOtpResendErrorKeys {
    public static final String REGISTRATION_AUTH_FLOW_INVALID = com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationPublicErrorCode.REGISTRATION_AUTHFLOW_INVALID.value();
    public static final String REGISTRATION_CSRF_INVALID = com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationPublicErrorCode.REGISTRATION_CSRF_INVALID.value();
    public static final String REGISTRATION_OTP_COOLDOWN_ACTIVE = com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationPublicErrorCode.REGISTRATION_OTP_COOLDOWNACTIVE.value();
    public static final String REGISTRATION_OTP_MAX_ATTEMPTS_REACHED = com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationPublicErrorCode.REGISTRATION_OTP_LOCKED.value();
    public static final String REGISTRATION_OTP_RESEND_FAILED = com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationPublicErrorCode.REGISTRATION_MESSAGE_DELIVERYFAILED.value();
    public static final String WORKFLOW_IDEMPOTENCY_KEY_REUSED = com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationPublicErrorCode.WORKFLOW_IDEMPOTENCY_KEYREUSED.value();
    public static final String REGISTRATION_MESSAGE_DELIVERY_FAILED = com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationPublicErrorCode.REGISTRATION_MESSAGE_DELIVERYFAILED.value();

    private RegistrationOtpResendErrorKeys() {
    }
}
