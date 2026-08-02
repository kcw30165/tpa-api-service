package com.bct.ngtpa.apiservice.application.dto.auth.login.otp.verify;

public enum LoginOtpVerifyReservationOutcome {
    STARTED, EXISTING, REQUEST_HASH_MISMATCH, FAILED_EXISTING, UNKNOWN_OUTCOME_EXISTING, CORRUPT_STATE, SYSTEM_FAILURE
}
