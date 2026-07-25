package com.bct.ngtpa.apiservice.application.usecase.auth.logout;

import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import java.util.Objects;

public record AuthenticatedLogoutCommand(
        AuthenticatedSessionId authenticatedSessionId,
        String correlationId) {
    public AuthenticatedLogoutCommand {
        Objects.requireNonNull(authenticatedSessionId, "authenticatedSessionId must not be null");
        correlationId = correlationId == null || correlationId.isBlank()
                ? null
                : correlationId.trim();
    }

    @Override
    public String toString() {
        return "AuthenticatedLogoutCommand[authenticatedSessionId=<redacted>, correlationId=<redacted>]";
    }
}
