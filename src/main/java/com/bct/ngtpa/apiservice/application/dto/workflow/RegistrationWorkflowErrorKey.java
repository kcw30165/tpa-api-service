package com.bct.ngtpa.apiservice.application.dto.workflow;

/** Canonical public error keys owned by the registration workflow. */
public enum RegistrationWorkflowErrorKey {
    AUTH_FLOW_INVALID("err.registration.authFlow.invalid"),
    CSRF_INVALID("err.registration.csrf.invalid"),
    ORIGIN_INVALID("err.registration.origin.invalid"),
    STATE_INVALID("err.registration.state.invalid"),
    START_REQUEST_INVALID("err.registration.start.request.invalid"),
    START_RATE_LIMITED("err.registration.start.rateLimited"),
    START_VERIFICATION_FAILED("err.registration.start.verificationFailed"),
    OTP_REQUIRED("err.registration.otp.required"),
    OTP_INVALID("err.registration.otp.invalid"),
    OTP_EXPIRED("err.registration.otp.expired"),
    OTP_LOCKED("err.registration.otp.locked"),
    OTP_COOLDOWN_ACTIVE("err.registration.otp.cooldownActive"),
    OTP_SEND_RATE_LIMITED("err.registration.otp.sendRateLimited"),
    OTP_VERIFY_RATE_LIMITED("err.registration.otp.verifyRateLimited"),
    MESSAGE_DELIVERY_FAILED("err.registration.message.deliveryFailed"),
    TRUST_SELECTION_INVALID("err.registration.trust.selectionInvalid"),
    USERNAME_REQUEST_INVALID("err.registration.username.request.invalid"),
    USERNAME_POLICY_INVALID("err.registration.username.policyInvalid"),
    USERNAME_ALREADY_REGISTERED("err.registration.username.isRegistered"),
    COMPLETE_REQUEST_INVALID("err.registration.complete.request.invalid"),
    COMPLETE_MEMBER_NOT_FOUND("err.registration.complete.memberNotFound"),
    COMPLETE_MEMBER_ALREADY_REGISTERED("err.registration.complete.memberAlreadyRegistered");

    private final String value;
    RegistrationWorkflowErrorKey(String value) { this.value = value; }
    public String value() { return value; }
}
