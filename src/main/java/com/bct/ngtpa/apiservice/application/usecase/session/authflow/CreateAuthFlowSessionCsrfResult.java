package com.bct.ngtpa.apiservice.application.usecase.session.authflow;

import java.time.Instant;
import java.util.Objects;

public record CreateAuthFlowSessionCsrfResult(String rawToken, Instant expiresAt, int expiresInSeconds) {
    public CreateAuthFlowSessionCsrfResult {
        Objects.requireNonNull(rawToken, "rawToken must not be null");
        if (rawToken.isBlank()) {
            throw new IllegalArgumentException("rawToken must not be blank");
        }
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        if (expiresInSeconds <= 0) {
            throw new IllegalArgumentException("expiresInSeconds must be positive");
        }
    }
    @Override public String toString() { return "CreateAuthFlowSessionCsrfResult[rawToken=<redacted>, expiresAt=" + expiresAt + ", expiresInSeconds=" + expiresInSeconds + "]"; }
}
