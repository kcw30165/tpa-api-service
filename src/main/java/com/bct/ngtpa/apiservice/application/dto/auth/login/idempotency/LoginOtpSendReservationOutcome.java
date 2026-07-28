package com.bct.ngtpa.apiservice.application.dto.auth.login.idempotency;

public enum LoginOtpSendReservationOutcome {
    STARTED, DUPLICATE_IN_PROGRESS, COMPLETED_REPLAY, KEY_REUSED, FAILED_EXISTING, UNKNOWN_OUTCOME
}
