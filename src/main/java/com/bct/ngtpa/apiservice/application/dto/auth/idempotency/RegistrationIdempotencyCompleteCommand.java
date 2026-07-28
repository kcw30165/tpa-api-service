package com.bct.ngtpa.apiservice.application.dto.auth.idempotency;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public record RegistrationIdempotencyCompleteCommand(
        RegistrationIdempotencyAction action,
        RegistrationIdempotencyKey idempotencyKey,
        RegistrationRequestHash requestHash,
        RegistrationIdempotencyResponseSnapshot responseSnapshot,
        Instant completedAt,
        Duration ttl) {
    public RegistrationIdempotencyCompleteCommand {
        Objects.requireNonNull(action, "action must not be null");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
        Objects.requireNonNull(requestHash, "requestHash must not be null");
        Objects.requireNonNull(responseSnapshot, "responseSnapshot must not be null");
        Objects.requireNonNull(completedAt, "completedAt must not be null");
        Objects.requireNonNull(ttl, "ttl must not be null");
        if (!ttl.isPositive()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
    }

    @Override
    public String toString() {
        return "RegistrationIdempotencyCompleteCommand[action=" + action + ", completedAt=" + completedAt + ", ttl="
                + ttl
                + ", idempotencyKey=<redacted>, requestHash=<redacted>, responseSnapshot=<redacted>]";
    }
}
