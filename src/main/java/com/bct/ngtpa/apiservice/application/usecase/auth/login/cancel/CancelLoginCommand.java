package com.bct.ngtpa.apiservice.application.usecase.auth.login.cancel;

import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import java.util.Objects;

public record CancelLoginCommand(AuthFlowSessionId authFlowSessionId, String csrfToken, String origin, String referer,
        String correlationId) {
    public CancelLoginCommand {
        Objects.requireNonNull(authFlowSessionId, "authFlowSessionId must not be null");
    }

    @Override
    public String toString() {
        return "CancelLoginCommand[authFlowSessionId=<redacted>, csrfToken=<redacted>, origin=<redacted>, referer=<redacted>, correlationId=<redacted>]";
    }
}
