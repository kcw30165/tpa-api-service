package com.bct.ngtpa.apiservice.application.dto.auth.login.otp.idempotency;

import java.time.*;

public record LoginOtpSendReserveCommand(String idempotencyKey, LoginOtpSendRequestHash requestHash, Instant now,
        Duration ttl) {
    public LoginOtpSendReserveCommand {
        if (idempotencyKey == null || idempotencyKey.isBlank() || requestHash == null || now == null || ttl == null
                || ttl.isZero() || ttl.isNegative())
            throw new IllegalArgumentException("Invalid OTP send reservation");
    }

    @Override
    public String toString() {
        return "LoginOtpSendReserveCommand[idempotencyKey=<redacted>, requestHash=<redacted>, now=" + now + ", ttl="
                + ttl + "]";
    }
}
