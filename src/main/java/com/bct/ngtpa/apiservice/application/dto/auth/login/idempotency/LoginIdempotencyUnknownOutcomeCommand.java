package com.bct.ngtpa.apiservice.application.dto.auth.login.idempotency;

import java.time.*;

public record LoginIdempotencyUnknownOutcomeCommand(LoginIdempotencyAction action, LoginIdempotencyKey idempotencyKey,
        LoginCredentialRequestHash requestHash, Instant recordedAt, Duration ttl) {
}
