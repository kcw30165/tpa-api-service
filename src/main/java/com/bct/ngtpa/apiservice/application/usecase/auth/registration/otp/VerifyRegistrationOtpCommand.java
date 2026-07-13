package com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp;

import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyKey;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import java.time.Duration;
import java.util.Objects;

public record VerifyRegistrationOtpCommand(
        AuthFlowSessionId authFlowSessionId,
        String csrfToken,
        String otpCode,
        String origin,
        String referer,
        RegistrationIdempotencyKey idempotencyKey,
        String acceptLanguage,
        String correlationId,
        Duration idempotencyTtl) {
    public VerifyRegistrationOtpCommand {
        Objects.requireNonNull(authFlowSessionId, "authFlowSessionId must not be null");
        if (csrfToken == null || csrfToken.isBlank()) { throw new IllegalArgumentException("csrfToken must not be blank"); }
        Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
        idempotencyTtl = Objects.requireNonNull(idempotencyTtl, "idempotencyTtl must not be null");
    }
    @Override public String toString() {
        return "VerifyRegistrationOtpCommand[authFlowSessionId=<redacted>, csrfToken=<redacted>, otpCode=<redacted>, idempotencyKey=<redacted>, origin=<redacted>, referer=<redacted>]";
    }
}
