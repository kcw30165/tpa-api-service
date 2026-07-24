package com.bct.ngtpa.apiservice.application.dto.auth.login.idempotency;

import java.time.*;

public record LoginOtpSendTerminalCommand(String idempotencyKey, LoginOtpSendRequestHash requestHash, String reason,
        Instant now, Duration ttl) {
    @Override
    public String toString() {
        return "LoginOtpSendTerminalCommand[idempotencyKey=<redacted>, requestHash=<redacted>, reason=" + reason
                + ", now=" + now + ", ttl=" + ttl + "]";
    }
}
