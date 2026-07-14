package com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete;

public final class CompleteRegistrationErrorKeys {
    public static final String PASSWORD_REQUIRED = "registration.password.required";
    public static final String CONFIRM_PASSWORD_REQUIRED = "registration.confirmPassword.required";
    public static final String PASSWORD_MISMATCH = "registration.password.mismatch";
    public static final String PASSWORD_MIN_LENGTH = "registration.password.minLength";
    public static final String PASSWORD_MAX_LENGTH = "registration.password.maxLength";
    public static final String PASSWORD_SPACES = "registration.password.noSpaces";
    public static final String PASSWORD_SAME_AS_USERNAME = "registration.password.sameAsUsername";
    public static final String PASSWORD_CATEGORIES = "registration.password.requiredCategories";

    private CompleteRegistrationErrorKeys() {
    }
}
