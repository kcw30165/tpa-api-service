package com.bct.ngtpa.apiservice.application.dto.workflow;

/** Canonical public errors owned by the registration workflow. */
public enum RegistrationErrorKey implements WorkflowErrorKey {
    AUTH_FLOW_INVALID("err.registration.authFlow.invalid"),
    COMPLETE_MEMBER_ALREADY_REGISTERED("err.registration.complete.memberAlreadyRegistered"),
    COMPLETE_MEMBER_NOT_FOUND("err.registration.complete.memberNotFound"),
    COMPLETE_REQUEST_INVALID("err.registration.complete.request.invalid"),
    CSRF_INVALID("err.registration.csrf.invalid"),
    MESSAGE_DELIVERY_FAILED("err.registration.message.deliveryFailed"),
    ORIGIN_INVALID("err.registration.origin.invalid"),
    OTP_COOLDOWN_ACTIVE("err.registration.otp.cooldownActive"),
    OTP_EXPIRED("err.registration.otp.expired"),
    OTP_INVALID("err.registration.otp.invalid"),
    OTP_LOCKED("err.registration.otp.locked"),
    OTP_REQUIRED("err.registration.otp.required"),
    OTP_SEND_RATE_LIMITED("err.registration.otp.sendRateLimited"),
    OTP_VERIFY_RATE_LIMITED("err.registration.otp.verifyRateLimited"),
    REGISTRATION_CAPTCHA_CONSUMED("err.registration.captcha.consumed"),
    REGISTRATION_CAPTCHA_EXPIRED("err.registration.captcha.expired"),
    REGISTRATION_CAPTCHA_INVALID("err.registration.captcha.invalid"),
    REGISTRATION_CAPTCHA_LOCKED("err.registration.captcha.locked"),
    REGISTRATION_CAPTCHA_NOTFOUND("err.registration.captcha.notFound"),
    REGISTRATION_CAPTCHA_REFRESHFAILED("err.registration.captcha.refreshFailed"),
    REGISTRATION_CAPTCHA_REQUIRED("err.registration.captcha.required"),
    REGISTRATION_COMPLETE_CONTINUATIONUNAVAILABLE("err.registration.complete.continuationUnavailable"),
    REGISTRATION_COMPLETE_FAILED("err.registration.complete.failed"),
    REGISTRATION_COMPLETE_OUTCOMEUNKNOWN("err.registration.complete.outcomeUnknown"),
    REGISTRATION_CONFIRMPASSWORD_REQUIRED("err.registration.confirmPassword.required"),
    REGISTRATION_CONTACT_EXACTLYONEREQUIRED("err.registration.contact.exactlyOneRequired"),
    REGISTRATION_CONTACT_REQUIRED("err.registration.contact.required"),
    REGISTRATION_DATEOFBIRTH_INVALID("err.registration.dateOfBirth.invalid"),
    REGISTRATION_EMAIL_INVALID("err.registration.email.invalid"),
    REGISTRATION_IDENTITYTYPE_INVALID("err.registration.identityType.invalid"),
    REGISTRATION_IDENTITYVALUE_INVALID("err.registration.identityValue.invalid"),
    REGISTRATION_MOBILE_INVALID("err.registration.mobile.invalid"),
    REGISTRATION_PASSWORD_MAXLENGTH("err.registration.password.maxLength"),
    REGISTRATION_PASSWORD_MINLENGTH("err.registration.password.minLength"),
    REGISTRATION_PASSWORD_MISMATCH("err.registration.password.mismatch"),
    REGISTRATION_PASSWORD_NOSPACES("err.registration.password.noSpaces"),
    REGISTRATION_PASSWORD_REQUIRED("err.registration.password.required"),
    REGISTRATION_PASSWORD_REQUIREDCATEGORIES("err.registration.password.requiredCategories"),
    REGISTRATION_PASSWORD_SAMEASUSERNAME("err.registration.password.sameAsUsername"),
    REGISTRATION_PASSWORD_VALIDATIONFAILED("err.registration.password.validationFailed"),
    REGISTRATION_TRUSTLOOKUP_FAILED("err.registration.trustLookup.failed"),
    REGISTRATION_TRUSTLOOKUP_NOELIGIBLETRUSTS("err.registration.trustLookup.noEligibleTrusts"),
    REGISTRATION_USERNAME_CHECKFAILED("err.registration.username.checkFailed"),
    REGISTRATION_USERNAME_CHECK_FAILED("err.registration.username.check.failed"),
    START_RATE_LIMITED("err.registration.start.rateLimited"),
    START_REQUEST_INVALID("err.registration.start.request.invalid"),
    START_VERIFICATION_FAILED("err.registration.start.verificationFailed"),
    STATE_INVALID("err.registration.state.invalid"),
    TRUST_SELECTION_INVALID("err.registration.trust.selectionInvalid"),
    USERNAME_ALREADY_REGISTERED("err.registration.username.isRegistered"),
    USERNAME_POLICY_INVALID("err.registration.username.policyInvalid"),
    USERNAME_REQUEST_INVALID("err.registration.username.request.invalid");

    private final String value;

    RegistrationErrorKey(String value) {
        this.value = WorkflowKeyValidator.validate(
                value,
                "Invalid registration error key");
    }

    @Override
    public String value() {
        return value;
    }
}
