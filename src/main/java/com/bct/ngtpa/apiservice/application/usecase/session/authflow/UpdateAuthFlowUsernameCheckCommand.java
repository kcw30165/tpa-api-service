package com.bct.ngtpa.apiservice.application.usecase.session.authflow;

import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.UsernameCheckState;
import java.util.Objects;

public record UpdateAuthFlowUsernameCheckCommand(AuthFlowSessionId authFlowSessionId, UsernameCheckState usernameCheckState, String correlationId) {
    public UpdateAuthFlowUsernameCheckCommand { Objects.requireNonNull(authFlowSessionId, "authFlowSessionId must not be null"); Objects.requireNonNull(usernameCheckState, "usernameCheckState must not be null"); }
    @Override public String toString() { return "UpdateAuthFlowUsernameCheckCommand[authFlowSessionId=<redacted>, usernameCheckState=<redacted>, correlationId=" + correlationId + "]"; }
}
