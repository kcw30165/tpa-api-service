package com.bct.ngtpa.apiservice.application.dto.session.authenticated;
import java.util.Objects;
public record LoadAuthenticatedSessionForReplayCommand(AuthenticatedSessionId authenticatedSessionId,
        String correlationId) {
    public LoadAuthenticatedSessionForReplayCommand {
        Objects.requireNonNull(authenticatedSessionId);
        correlationId = correlationId == null || correlationId.isBlank() ? null : correlationId.trim();
    }
}
