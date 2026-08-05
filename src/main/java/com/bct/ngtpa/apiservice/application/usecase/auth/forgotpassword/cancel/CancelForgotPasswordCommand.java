package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.cancel;

import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import java.util.Objects;

public record CancelForgotPasswordCommand(
        AuthFlowSessionId authFlowSessionId,
        String csrfToken,
        String origin,
        String referer,
        String correlationId) {

    public CancelForgotPasswordCommand {
        Objects.requireNonNull(authFlowSessionId, "authFlowSessionId must not be null");
    }

    @Override
    public String toString() {
        return "CancelForgotPasswordCommand[authFlowSessionId=<redacted>, csrfToken=<redacted>, "
                + "origin=<redacted>, referer=<redacted>, correlationId="
                + (correlationId == null ? "" : "<redacted>") + "]";
    }
}