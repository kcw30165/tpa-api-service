package com.bct.ngtpa.apiservice.application.usecase.session.authflow;

import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import java.util.Objects;

public record LoadAuthFlowSessionCommand(AuthFlowSessionId authFlowSessionId, String correlationId) {
    public LoadAuthFlowSessionCommand {
        Objects.requireNonNull(authFlowSessionId, "authFlowSessionId must not be null");
    }

    @Override
    public String toString() {
        return "LoadAuthFlowSessionCommand[authFlowSessionId=<redacted>, correlationId=" + correlationId + "]";
    }
}
