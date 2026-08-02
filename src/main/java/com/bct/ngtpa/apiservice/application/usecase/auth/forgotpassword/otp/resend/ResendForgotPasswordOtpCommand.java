package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.resend;

import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import java.util.Objects;

public record ResendForgotPasswordOtpCommand(
        AuthFlowSessionId sessionId,
        String csrfToken,
        String origin,
        String referer,
        String requestId,
        String language,
        String idempotencyKey) {
    public ResendForgotPasswordOtpCommand {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        if (csrfToken == null || csrfToken.isBlank()) {
            throw new IllegalArgumentException("csrfToken must not be blank");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey must not be blank");
        }
    }
}