package com.bct.ngtpa.apiservice.application.dto.auth.login.otp.idempotency;

public record LoginOtpSendReservation(LoginOtpSendReservationOutcome outcome, LoginOtpSendReplaySnapshot replay) {
    public LoginOtpSendReservation {
        if (outcome == null)
            throw new IllegalArgumentException("outcome required");
        if (outcome == LoginOtpSendReservationOutcome.COMPLETED_REPLAY && replay == null)
            throw new IllegalArgumentException("replay required");
    }
}
