package com.bct.ngtpa.apiservice.application.usecase.session.authenticated;

import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import java.util.Objects;

public record LoadAndTouchAuthenticatedSessionCommand(AuthenticatedSessionId authenticatedSessionId,
        String correlationId) {
    public LoadAndTouchAuthenticatedSessionCommand {
        Objects.requireNonNull(authenticatedSessionId);
        correlationId = correlationId == null || correlationId.isBlank() ? null : correlationId.trim();
    }
}
