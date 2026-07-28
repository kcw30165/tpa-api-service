package com.bct.ngtpa.apiservice.application.usecase.session.authflow;

import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import java.util.Objects;

public record DeleteAuthFlowSessionCommand(AuthFlowSessionId authFlowSessionId, String correlationId) {
    public DeleteAuthFlowSessionCommand {
        Objects.requireNonNull(authFlowSessionId, "authFlowSessionId must not be null");
    }

    @Override
    public String toString() {
        return "DeleteAuthFlowSessionCommand[authFlowSessionId=<redacted>, correlationId=" + correlationId + "]";
    }
}
