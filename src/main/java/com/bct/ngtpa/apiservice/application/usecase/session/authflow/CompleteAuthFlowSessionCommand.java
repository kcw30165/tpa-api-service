package com.bct.ngtpa.apiservice.application.usecase.session.authflow;

import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import java.util.Objects;

public record CompleteAuthFlowSessionCommand(AuthFlowSessionId authFlowSessionId, String correlationId) {
    public CompleteAuthFlowSessionCommand { Objects.requireNonNull(authFlowSessionId, "authFlowSessionId must not be null"); }
    @Override public String toString() { return "CompleteAuthFlowSessionCommand[authFlowSessionId=<redacted>, correlationId=" + correlationId + "]"; }
}
