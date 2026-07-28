package com.bct.ngtpa.apiservice.application.usecase.auth.login.otp;

public enum SendLoginOtpStatus {
    AUTH_FLOW_INVALID,
    SENT,
    INVALID_DESTINATION,
    DELIVERY_FAILED,
    DUPLICATE_IN_PROGRESS,
    COMPLETED_REPLAY,
    KEY_REUSED,
    FAILED_EXISTING,
    UNKNOWN_OUTCOME,
    CSRF_INVALID
}
