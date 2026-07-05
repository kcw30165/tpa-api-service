package com.bct.ngtpa.apiservice.application.service.auth.registration.context;

import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import java.util.Objects;

public record RegistrationProtectedAuthFlowContextCommand(
        AuthFlowSessionId authFlowSessionId,
        String csrfToken,
        String origin,
        String referer,
        String correlationId) {
    public RegistrationProtectedAuthFlowContextCommand {
        Objects.requireNonNull(authFlowSessionId, "authFlowSessionId must not be null");
        if (csrfToken == null || csrfToken.isBlank()) {
            throw new IllegalArgumentException("csrfToken must not be blank");
        }
    }

    @Override
    public String toString() {
        return "RegistrationProtectedAuthFlowContextCommand[authFlowSessionId=<redacted>, csrfToken=<redacted>, origin=<redacted>, referer=<redacted>, correlationId="
                + (correlationId == null ? "" : "<redacted>") + "]";
    }
}
