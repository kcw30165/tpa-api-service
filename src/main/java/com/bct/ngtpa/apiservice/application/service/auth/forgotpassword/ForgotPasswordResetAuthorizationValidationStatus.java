package com.bct.ngtpa.apiservice.application.service.auth.forgotpassword;

public enum ForgotPasswordResetAuthorizationValidationStatus {
    VALID,
    WRONG_SESSION,
    WRONG_SCENARIO,
    WRONG_SUBJECT,
    EXPIRED,
    CONSUMED,
    TERMINAL_SESSION,
    UNKNOWN_OUTCOME_BLOCKED
}