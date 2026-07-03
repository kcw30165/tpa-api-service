package com.bct.ngtpa.apiservice.application.dto.workflow;

public final class RegistrationWorkflowConstants {
    public static final String PAGE_REGISTRATION_START = "registration-start";
    public static final String PAGE_REGISTRATION_OTP_VERIFICATION = "registration-otp-verification";
    public static final String PAGE_REGISTRATION_TRUST_SELECTION = "registration-trust-selection";
    public static final String PAGE_REGISTRATION_USERNAME_CHECK = "registration-username-check";
    public static final String PAGE_REGISTRATION_PASSWORD_SETUP = "registration-password-setup";
    public static final String PAGE_REGISTRATION_SUCCESS = "registration-success";

    public static final String SUCCESS_VARIANT_NO_REMAINING_TRUSTS = "noRemainingTrusts";
    public static final String SUCCESS_VARIANT_HAS_REMAINING_TRUSTS = "hasRemainingTrusts";

    private RegistrationWorkflowConstants() {
    }
}
