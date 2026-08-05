package com.bct.ngtpa.apiservice.application.dto.auth.login.otp.verify;

import java.util.Objects;

public record LoginOtpVerifyReservationResult(LoginOtpVerifyReservationOutcome outcome,
        LoginOtpVerifyReservation reservation) {
    public LoginOtpVerifyReservationResult {
        Objects.requireNonNull(outcome);
        if ((outcome == LoginOtpVerifyReservationOutcome.STARTED
                || outcome == LoginOtpVerifyReservationOutcome.EXISTING) && reservation == null)
            throw new IllegalArgumentException("reservation required");
    }
}
