package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.complete;

public enum CompleteForgotPasswordStatus {
    SUCCESS,
    VALIDATION_FAILED,
    INVALID_AUTH_FLOW,
    CSRF_INVALID,
    IDEMPOTENCY_KEY_REUSED,
    DUPLICATE_IN_PROGRESS,
    BUSINESS_REJECTED,
    UNKNOWN_OUTCOME,
    SYSTEM_FAILED
}