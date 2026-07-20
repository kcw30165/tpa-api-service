package com.bct.ngtpa.apiservice.application.usecase.auth.registration;

public enum StartRegistrationResultStatus {
    STARTED,
    VALIDATION_FAILED,
    VERIFICATION_FAILED,
    DOWNSTREAM_REJECTED,
    DUPLICATE_IN_PROGRESS,
    COMPLETED_REPLAY,
    IDEMPOTENCY_KEY_REUSED,
    MESSAGE_DELIVERY_FAILED,
    SYSTEM_FAILED
}
