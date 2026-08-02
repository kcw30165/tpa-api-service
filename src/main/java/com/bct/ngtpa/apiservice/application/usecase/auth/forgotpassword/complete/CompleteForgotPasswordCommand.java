package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.complete;

import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyKey;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import java.time.Duration;
import java.util.Objects;

public record CompleteForgotPasswordCommand(
        AuthFlowSessionId authFlowSessionId,
        String csrfToken,
        ForgotPasswordIdempotencyKey idempotencyKey,
        String password,
        String confirmPassword,
        String origin,
        String referer,
        String acceptLanguage,
        String correlationId,
        Duration idempotencyTtl) {

    public CompleteForgotPasswordCommand {
        Objects.requireNonNull(authFlowSessionId, "authFlowSessionId must not be null");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
        Objects.requireNonNull(idempotencyTtl, "idempotencyTtl must not be null");
        if (!idempotencyTtl.isPositive()) {
            throw new IllegalArgumentException("idempotencyTtl must be positive");
        }
    }

    @Override
    public String toString() {
        return "CompleteForgotPasswordCommand[authFlowSessionId=<redacted>, csrfToken=<redacted>, "
                + "idempotencyKey=<redacted>, password=<redacted>, confirmPassword=<redacted>, "
                + "origin=<redacted>, referer=<redacted>, acceptLanguage=" + acceptLanguage
                + ", correlationId=<redacted>, idempotencyTtl=" + idempotencyTtl + "]";
    }
}