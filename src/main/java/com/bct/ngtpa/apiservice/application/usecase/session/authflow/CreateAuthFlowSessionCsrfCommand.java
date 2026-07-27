package com.bct.ngtpa.apiservice.application.usecase.session.authflow;

import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import java.time.Instant;
import java.util.Objects;

public record CreateAuthFlowSessionCsrfCommand(AuthFlowSessionId authFlowSessionId, Instant now, int ttlSeconds,
        String correlationId) {
    public CreateAuthFlowSessionCsrfCommand {
        Objects.requireNonNull(authFlowSessionId, "authFlowSessionId must not be null");
        Objects.requireNonNull(now, "now must not be null");
        if (ttlSeconds <= 0) {
            throw new IllegalArgumentException("ttlSeconds must be positive");
        }
    }

    @Override
    public String toString() {
        return "CreateAuthFlowSessionCsrfCommand[authFlowSessionId=<redacted>, now=" + now + ", ttlSeconds="
                + ttlSeconds + ", correlationId=" + correlationId + "]";
    }
}
