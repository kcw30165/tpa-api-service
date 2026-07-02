package com.bct.ngtpa.apiservice.application.usecase.session.authflow;

import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import java.util.Objects;

public record UpdateAuthFlowOtpCommand(AuthFlowSessionId authFlowSessionId, String activeOtpChallengeId, String correlationId) {
    public UpdateAuthFlowOtpCommand { Objects.requireNonNull(authFlowSessionId, "authFlowSessionId must not be null"); if (activeOtpChallengeId == null || activeOtpChallengeId.isBlank()) throw new IllegalArgumentException("activeOtpChallengeId must not be blank"); }
    @Override public String toString() { return "UpdateAuthFlowOtpCommand[authFlowSessionId=<redacted>, activeOtpChallengeId=<redacted>, correlationId=" + correlationId + "]"; }
}
