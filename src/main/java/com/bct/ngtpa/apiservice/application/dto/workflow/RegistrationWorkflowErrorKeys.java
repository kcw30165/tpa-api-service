package com.bct.ngtpa.apiservice.application.dto.workflow;

public final class RegistrationWorkflowErrorKeys {
    public static final WorkflowErrorKey REGISTRATION_START_INVALID = new WorkflowErrorKey("registration.start.invalid");
    public static final WorkflowErrorKey REGISTRATION_START_VERIFICATION_FAILED = new WorkflowErrorKey("registration.start.verificationFailed");
    public static final WorkflowErrorKey REGISTRATION_CONTACT_REQUIRED = new WorkflowErrorKey("registration.contact.required");
    public static final WorkflowErrorKey REGISTRATION_CONTACT_EXACTLY_ONE_REQUIRED = new WorkflowErrorKey("registration.contact.exactlyOneRequired");
    public static final WorkflowErrorKey REGISTRATION_IDENTITY_TYPE_INVALID = new WorkflowErrorKey("registration.identityType.invalid");
    public static final WorkflowErrorKey REGISTRATION_IDENTITY_VALUE_INVALID = new WorkflowErrorKey("registration.identityValue.invalid");
    public static final WorkflowErrorKey REGISTRATION_DATE_OF_BIRTH_INVALID = new WorkflowErrorKey("registration.dateOfBirth.invalid");
    public static final WorkflowErrorKey REGISTRATION_AUTH_FLOW_INVALID = new WorkflowErrorKey("registration.authFlow.invalid");
    public static final WorkflowErrorKey REGISTRATION_CSRF_INVALID = new WorkflowErrorKey("registration.csrf.invalid");
    public static final WorkflowErrorKey REGISTRATION_ORIGIN_INVALID = new WorkflowErrorKey("registration.origin.invalid");
    public static final WorkflowErrorKey REGISTRATION_OTP_INVALID = new WorkflowErrorKey("registration.otp.invalid");
    public static final WorkflowErrorKey REGISTRATION_OTP_EXPIRED = new WorkflowErrorKey("registration.otp.expired");
    public static final WorkflowErrorKey REGISTRATION_OTP_MAX_ATTEMPTS = new WorkflowErrorKey("registration.otp.maxAttemptsReached");
    public static final WorkflowErrorKey REGISTRATION_USERNAME_UNAVAILABLE = new WorkflowErrorKey("registration.username.unavailable");
    public static final WorkflowErrorKey REGISTRATION_USERNAME_CHECK_FAILED = new WorkflowErrorKey("registration.username.checkFailed");
    public static final WorkflowErrorKey REGISTRATION_COMPLETE_FAILED = new WorkflowErrorKey("registration.complete.failed");
    public static final WorkflowErrorKey REGISTRATION_PASSWORD_VALIDATION_FAILED = new WorkflowErrorKey("registration.password.validationFailed");
    public static final WorkflowErrorKey REGISTRATION_MESSAGE_DELIVERY_FAILED = new WorkflowErrorKey("registration.messageDelivery.failed");
    public static final WorkflowErrorKey REGISTRATION_TRUST_LOOKUP_FAILED = new WorkflowErrorKey("registration.trustLookup.failed");
    public static final WorkflowErrorKey REGISTRATION_NO_ELIGIBLE_TRUSTS = new WorkflowErrorKey("registration.trustLookup.noEligibleTrusts");
    public static final WorkflowErrorKey WORKFLOW_IDEMPOTENCY_DUPLICATE_IN_PROGRESS = new WorkflowErrorKey("workflow.idempotency.duplicateInProgress");
    public static final WorkflowErrorKey WORKFLOW_IDEMPOTENCY_KEY_REUSED = new WorkflowErrorKey("workflow.idempotency.keyReused");
    public static final WorkflowErrorKey WORKFLOW_SYSTEM_UNAVAILABLE = new WorkflowErrorKey("workflow.system.unavailable");
    public static final WorkflowErrorKey WORKFLOW_DOWNSTREAM_UNAVAILABLE = new WorkflowErrorKey("workflow.downstream.unavailable");

    /** P6 mapping note only: APIM err.tpa.member.not.found maps to REGISTRATION_START_VERIFICATION_FAILED. */
    public static final String APIM_MEMBER_NOT_FOUND = "err.tpa.member.not.found";
    public static final WorkflowErrorKey P6_MEMBER_NOT_FOUND_TARGET_KEY = REGISTRATION_START_VERIFICATION_FAILED;


    public static final WorkflowErrorKey REGISTRATION_EMAIL_INVALID = new WorkflowErrorKey("registration.email.invalid");
    public static final WorkflowErrorKey REGISTRATION_MOBILE_INVALID = new WorkflowErrorKey("registration.mobile.invalid");
    public static final WorkflowErrorKey REGISTRATION_CAPTCHA_INVALID = new WorkflowErrorKey("registration.captcha.invalid");
    public static final WorkflowErrorKey REGISTRATION_CAPTCHA_EXPIRED = new WorkflowErrorKey("registration.captcha.expired");
    public static final WorkflowErrorKey REGISTRATION_CAPTCHA_LOCKED = new WorkflowErrorKey("registration.captcha.locked");
    public static final WorkflowErrorKey REGISTRATION_CAPTCHA_NOT_FOUND = new WorkflowErrorKey("registration.captcha.notFound");
    public static final WorkflowErrorKey REGISTRATION_CAPTCHA_CONSUMED = new WorkflowErrorKey("registration.captcha.consumed");
    public static final WorkflowErrorKey REGISTRATION_CAPTCHA_REFRESH_FAILED = new WorkflowErrorKey("registration.captcha.refreshFailed");
    public static final WorkflowErrorKey REGISTRATION_CAPTCHA_REQUIRED = new WorkflowErrorKey("registration.captcha.required");

    public static final WorkflowErrorKey WORKFLOW_IDEMPOTENCY_MISSING = new WorkflowErrorKey("workflow.idempotency.missing");

    private RegistrationWorkflowErrorKeys() {
    }
}
