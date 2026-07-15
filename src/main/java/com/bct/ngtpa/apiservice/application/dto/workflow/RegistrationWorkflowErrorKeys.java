package com.bct.ngtpa.apiservice.application.dto.workflow;

public final class RegistrationWorkflowErrorKeys {
    public static final WorkflowErrorKey REGISTRATION_START_INVALID = new WorkflowErrorKey(com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationPublicErrorCode.REGISTRATION_START_REQUEST_INVALID.value());
    public static final WorkflowErrorKey REGISTRATION_START_VERIFICATION_FAILED = new WorkflowErrorKey(com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationPublicErrorCode.REGISTRATION_START_VERIFICATIONFAILED.value());
    public static final WorkflowErrorKey REGISTRATION_CONTACT_REQUIRED = new WorkflowErrorKey(com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationPublicErrorCode.REGISTRATION_CONTACT_REQUIRED.value());
    public static final WorkflowErrorKey REGISTRATION_CONTACT_EXACTLY_ONE_REQUIRED = new WorkflowErrorKey(com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationPublicErrorCode.REGISTRATION_CONTACT_EXACTLYONEREQUIRED.value());
    public static final WorkflowErrorKey REGISTRATION_IDENTITY_TYPE_INVALID = new WorkflowErrorKey(com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationPublicErrorCode.REGISTRATION_IDENTITYTYPE_INVALID.value());
    public static final WorkflowErrorKey REGISTRATION_IDENTITY_VALUE_INVALID = new WorkflowErrorKey(com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationPublicErrorCode.REGISTRATION_IDENTITYVALUE_INVALID.value());
    public static final WorkflowErrorKey REGISTRATION_DATE_OF_BIRTH_INVALID = new WorkflowErrorKey(com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationPublicErrorCode.REGISTRATION_DATEOFBIRTH_INVALID.value());
    public static final WorkflowErrorKey REGISTRATION_AUTH_FLOW_INVALID = new WorkflowErrorKey(com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationPublicErrorCode.REGISTRATION_AUTHFLOW_INVALID.value());
    public static final WorkflowErrorKey REGISTRATION_CSRF_INVALID = new WorkflowErrorKey(com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationPublicErrorCode.REGISTRATION_CSRF_INVALID.value());
    public static final WorkflowErrorKey REGISTRATION_ORIGIN_INVALID = new WorkflowErrorKey(com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationPublicErrorCode.REGISTRATION_ORIGIN_INVALID.value());
    public static final WorkflowErrorKey REGISTRATION_OTP_INVALID = new WorkflowErrorKey(com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationPublicErrorCode.REGISTRATION_OTP_INVALID.value());
    public static final WorkflowErrorKey REGISTRATION_OTP_EXPIRED = new WorkflowErrorKey(com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationPublicErrorCode.REGISTRATION_OTP_EXPIRED.value());
    public static final WorkflowErrorKey REGISTRATION_OTP_MAX_ATTEMPTS = new WorkflowErrorKey(com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationPublicErrorCode.REGISTRATION_OTP_LOCKED.value());
    public static final WorkflowErrorKey REGISTRATION_USERNAME_UNAVAILABLE = new WorkflowErrorKey(com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationPublicErrorCode.REGISTRATION_USERNAME_ISREGISTERED.value());
    public static final WorkflowErrorKey REGISTRATION_USERNAME_CHECK_FAILED = new WorkflowErrorKey(com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationPublicErrorCode.REGISTRATION_USERNAME_CHECKFAILED.value());
    public static final WorkflowErrorKey REGISTRATION_COMPLETE_FAILED = new WorkflowErrorKey(com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationPublicErrorCode.WORKFLOW_DOWNSTREAM_BUSINESSREJECTED.value());
    public static final WorkflowErrorKey REGISTRATION_PASSWORD_VALIDATION_FAILED = new WorkflowErrorKey(com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationPublicErrorCode.REGISTRATION_PASSWORD_VALIDATIONFAILED.value());
    public static final WorkflowErrorKey REGISTRATION_MESSAGE_DELIVERY_FAILED = new WorkflowErrorKey(com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationPublicErrorCode.REGISTRATION_MESSAGE_DELIVERYFAILED.value());
    public static final WorkflowErrorKey REGISTRATION_TRUST_LOOKUP_FAILED = new WorkflowErrorKey(com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationPublicErrorCode.REGISTRATION_TRUSTLOOKUP_FAILED.value());
    public static final WorkflowErrorKey REGISTRATION_NO_ELIGIBLE_TRUSTS = new WorkflowErrorKey(com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationPublicErrorCode.REGISTRATION_TRUSTLOOKUP_NOELIGIBLETRUSTS.value());
    public static final WorkflowErrorKey WORKFLOW_IDEMPOTENCY_DUPLICATE_IN_PROGRESS = new WorkflowErrorKey(com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationPublicErrorCode.WORKFLOW_IDEMPOTENCY_INPROGRESS.value());
    public static final WorkflowErrorKey WORKFLOW_IDEMPOTENCY_KEY_REUSED = new WorkflowErrorKey(com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationPublicErrorCode.WORKFLOW_IDEMPOTENCY_KEYREUSED.value());
    public static final WorkflowErrorKey WORKFLOW_SYSTEM_UNAVAILABLE = new WorkflowErrorKey(com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationPublicErrorCode.WORKFLOW_SYSTEM_UNAVAILABLE.value());
    public static final WorkflowErrorKey WORKFLOW_DOWNSTREAM_UNAVAILABLE = new WorkflowErrorKey(com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationPublicErrorCode.WORKFLOW_DOWNSTREAM_UNAVAILABLE.value());

    /** P6 mapping note only: APIM err.tpa.member.not.found maps to REGISTRATION_START_VERIFICATION_FAILED. */
    public static final String APIM_MEMBER_NOT_FOUND = "err.tpa.member.not.found";
    public static final WorkflowErrorKey P6_MEMBER_NOT_FOUND_TARGET_KEY = REGISTRATION_START_VERIFICATION_FAILED;


    public static final WorkflowErrorKey REGISTRATION_EMAIL_INVALID = new WorkflowErrorKey(com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationPublicErrorCode.REGISTRATION_EMAIL_INVALID.value());
    public static final WorkflowErrorKey REGISTRATION_MOBILE_INVALID = new WorkflowErrorKey(com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationPublicErrorCode.REGISTRATION_MOBILE_INVALID.value());
    public static final WorkflowErrorKey REGISTRATION_CAPTCHA_INVALID = new WorkflowErrorKey(com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationPublicErrorCode.REGISTRATION_CAPTCHA_INVALID.value());
    public static final WorkflowErrorKey REGISTRATION_CAPTCHA_EXPIRED = new WorkflowErrorKey(com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationPublicErrorCode.REGISTRATION_CAPTCHA_EXPIRED.value());
    public static final WorkflowErrorKey REGISTRATION_CAPTCHA_LOCKED = new WorkflowErrorKey(com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationPublicErrorCode.REGISTRATION_CAPTCHA_LOCKED.value());
    public static final WorkflowErrorKey REGISTRATION_CAPTCHA_NOT_FOUND = new WorkflowErrorKey(com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationPublicErrorCode.REGISTRATION_CAPTCHA_NOTFOUND.value());
    public static final WorkflowErrorKey REGISTRATION_CAPTCHA_CONSUMED = new WorkflowErrorKey(com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationPublicErrorCode.REGISTRATION_CAPTCHA_CONSUMED.value());
    public static final WorkflowErrorKey REGISTRATION_CAPTCHA_REFRESH_FAILED = new WorkflowErrorKey(com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationPublicErrorCode.REGISTRATION_CAPTCHA_REFRESHFAILED.value());
    public static final WorkflowErrorKey REGISTRATION_CAPTCHA_REQUIRED = new WorkflowErrorKey(com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationPublicErrorCode.REGISTRATION_CAPTCHA_REQUIRED.value());

    public static final WorkflowErrorKey WORKFLOW_IDEMPOTENCY_MISSING = new WorkflowErrorKey(com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationPublicErrorCode.WORKFLOW_IDEMPOTENCY_MISSING.value());

    public static final WorkflowErrorKey REGISTRATION_USERNAME_INCORRECT = new WorkflowErrorKey(com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationPublicErrorCode.REGISTRATION_USERNAME_POLICYINVALID.value());
    public static final WorkflowErrorKey REGISTRATION_USERNAME_ISREGISTERED = new WorkflowErrorKey(com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationPublicErrorCode.REGISTRATION_USERNAME_ISREGISTERED.value());
    public static final WorkflowErrorKey REGISTRATION_USERNAME_CHECK_FAILED_CANONICAL = new WorkflowErrorKey(com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationPublicErrorCode.REGISTRATION_USERNAME_CHECK_FAILED.value());
    public static final WorkflowErrorKey REGISTRATION_SELECTED_TRUST_REQUIRED = new WorkflowErrorKey(com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationPublicErrorCode.REGISTRATION_TRUST_SELECTIONINVALID.value());
    public static final WorkflowErrorKey REGISTRATION_SELECTED_TRUST_INVALID = new WorkflowErrorKey(com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationPublicErrorCode.REGISTRATION_TRUST_SELECTIONINVALID.value());
    public static final WorkflowErrorKey WORKFLOW_STATE_INVALID = new WorkflowErrorKey(com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationPublicErrorCode.REGISTRATION_STATE_INVALID.value());
    public static final WorkflowErrorKey DOWNSTREAM_GENERIC_BUSINESS_REJECTED = new WorkflowErrorKey("downstream.generic.businessRejected");

    private RegistrationWorkflowErrorKeys() {
    }
}
