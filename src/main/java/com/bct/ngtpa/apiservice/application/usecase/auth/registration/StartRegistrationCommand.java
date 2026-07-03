package com.bct.ngtpa.apiservice.application.usecase.auth.registration;

import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyKey;
import java.time.Duration;
import java.util.Objects;

public record StartRegistrationCommand(
        RegistrationIdempotencyKey idempotencyKey,
        String identityType,
        String identityValue,
        String dateOfBirth,
        String email,
        String mobile,
        String captchaChallengeId,
        String acceptLanguage,
        String correlationId,
        Duration idempotencyTtl) {
    public StartRegistrationCommand {
        Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
        Objects.requireNonNull(idempotencyTtl, "idempotencyTtl must not be null");
        if (!idempotencyTtl.isPositive()) throw new IllegalArgumentException("idempotencyTtl must be positive");
    }
    @Override public String toString() {
        return "StartRegistrationCommand[idempotencyKey=<redacted>, identityType=" + identityType + ", identityValue=<redacted>, dateOfBirth=<redacted>, email=<redacted>, mobile=<redacted>, captchaChallengeId=<redacted>, acceptLanguage=<redacted>, correlationId=<redacted>, idempotencyTtl=" + idempotencyTtl + "]";
    }
}
