package com.bct.ngtpa.apiservice.application.dto.auth.login.idempotency;

import java.time.*;

public record LoginIdempotencyCompleteCommand(LoginIdempotencyAction action, LoginIdempotencyKey idempotencyKey,
        LoginCredentialRequestHash requestHash, LoginIdempotencyReplaySnapshot replaySnapshot, Instant completedAt,
        Duration ttl) {
}
