package com.bct.ngtpa.apiservice.application.service.auth.forgotpassword.context;

public enum ForgotPasswordProtectedAuthFlowContextStatus {
    VALID,
    MISSING_SESSION,
    EXPIRED_OR_TERMINAL_SESSION,
    WRONG_SCENARIO,
    INVALID_STATUS,
    MISSING_CONTEXT,
    CONTEXT_DECRYPTION_FAILED,
    ORIGIN_REFERER_INVALID,
    CSRF_INVALID
}