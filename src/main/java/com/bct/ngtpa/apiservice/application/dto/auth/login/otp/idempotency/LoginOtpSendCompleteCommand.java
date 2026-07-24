package com.bct.ngtpa.apiservice.application.dto.auth.login.otp.idempotency;

import java.time.*;

public record LoginOtpSendCompleteCommand(String idempotencyKey, LoginOtpSendRequestHash requestHash,
        LoginOtpSendReplaySnapshot replay, Instant now, Duration ttl) {
    @Override
    public String toString() {
        return "LoginOtpSendCompleteCommand[idempotencyKey=<redacted>, requestHash=<redacted>, replay=" + replay
                + ", now=" + now + ", ttl=" + ttl + "]";
    }
}
