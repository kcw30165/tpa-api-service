package com.bct.ngtpa.apiservice.application.usecase.session.authenticated;

import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import java.util.Objects;

public record ValidateAuthenticatedSessionCommand(
        AuthenticatedSessionId authenticatedSessionId,
        String submittedCsrfToken,
        String correlationId) {
    public ValidateAuthenticatedSessionCommand {
        Objects.requireNonNull(authenticatedSessionId, "authenticatedSessionId must not be null");
        if (submittedCsrfToken == null || submittedCsrfToken.isBlank()) {
            throw new IllegalArgumentException("submittedCsrfToken must not be blank");
        }
        submittedCsrfToken = submittedCsrfToken.trim();
        correlationId = correlationId == null || correlationId.isBlank() ? null : correlationId.trim();
    }
    @Override public String toString() {
        return "ValidateAuthenticatedSessionCommand[authenticatedSessionId=<redacted>, submittedCsrfToken=<redacted>, correlationId="
                + correlationId + "]";
    }
}
