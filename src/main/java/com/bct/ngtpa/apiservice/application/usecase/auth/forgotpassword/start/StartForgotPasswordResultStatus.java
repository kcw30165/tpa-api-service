package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.start;

public enum StartForgotPasswordResultStatus {
    STARTED,
    COMPLETED_REPLAY,
    DUPLICATE_IN_PROGRESS,
    IDEMPOTENCY_KEY_REUSED,
    USERNAME_NOT_FOUND,
    FAILED_EXISTING,
    UNKNOWN_OUTCOME,
    SYSTEM_FAILED
}