package com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete;

public final class CompleteRegistrationErrorKeys {
    public static final String PASSWORD_REQUIRED = com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationPublicErrorCode.REGISTRATION_PASSWORD_REQUIRED.value();
    public static final String CONFIRM_PASSWORD_REQUIRED = com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationPublicErrorCode.REGISTRATION_CONFIRMPASSWORD_REQUIRED.value();
    public static final String PASSWORD_MISMATCH = com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationPublicErrorCode.REGISTRATION_PASSWORD_MISMATCH.value();
    public static final String PASSWORD_MIN_LENGTH = com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationPublicErrorCode.REGISTRATION_PASSWORD_MINLENGTH.value();
    public static final String PASSWORD_MAX_LENGTH = com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationPublicErrorCode.REGISTRATION_PASSWORD_MAXLENGTH.value();
    public static final String PASSWORD_SPACES = com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationPublicErrorCode.REGISTRATION_PASSWORD_NOSPACES.value();
    public static final String PASSWORD_SAME_AS_USERNAME = com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationPublicErrorCode.REGISTRATION_PASSWORD_SAMEASUSERNAME.value();
    public static final String PASSWORD_CATEGORIES = com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationPublicErrorCode.REGISTRATION_PASSWORD_REQUIREDCATEGORIES.value();

    private CompleteRegistrationErrorKeys() {
    }
}
