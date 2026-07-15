package com.bct.ngtpa.apiservice.application.usecase.auth.registration.cancel;

public enum CancelRegistrationResultStatus {
    CANCELLED,
    SESSION_NOT_FOUND,
    ALREADY_CANCELLED,
    ALREADY_TERMINAL,
    INVALID_AUTH_FLOW,
    CSRF_INVALID,
    SYSTEM_FAILED
}
