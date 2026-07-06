package com.bct.ngtpa.apiservice.application.dto.auth.idempotency;

import java.util.Objects;

public record RegistrationIdempotencyStartResult(
        RegistrationIdempotencyStartOutcome outcome,
        RegistrationIdempotencyRecord record) {
    public RegistrationIdempotencyStartResult {
        Objects.requireNonNull(outcome, "outcome must not be null");
        Objects.requireNonNull(record, "record must not be null");
    }

    @Override
    public String toString() {
        return "RegistrationIdempotencyStartResult[outcome=" + outcome + ", record=<redacted>]";
    }
}
