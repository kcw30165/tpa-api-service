package com.bct.ngtpa.apiservice.application.dto.auth.idempotency;

public enum RegistrationIdempotencyStartOutcome {
    STARTED,
    DUPLICATE_IN_PROGRESS,
    COMPLETED_REPLAY,
    KEY_REUSED_WITH_DIFFERENT_REQUEST,
    FAILED_EXISTING,
    UNKNOWN_OUTCOME_EXISTING
}
