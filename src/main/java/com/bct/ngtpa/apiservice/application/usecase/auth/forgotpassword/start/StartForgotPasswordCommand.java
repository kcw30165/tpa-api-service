package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.start;

import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyKey;
import java.time.Duration;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import java.util.Objects;

public record StartForgotPasswordCommand(
        ForgotPasswordIdempotencyKey idempotencyKey,
        String username,
        String existingAuthFlowSessionId,
        String acceptLanguage,
        String correlationId,
        Duration idempotencyTtl,
        ActorUserRole actorUserRole) {
    public StartForgotPasswordCommand {
        Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username must not be blank");
        }
        Objects.requireNonNull(correlationId, "correlationId must not be null");
        Objects.requireNonNull(idempotencyTtl, "idempotencyTtl must not be null");
        Objects.requireNonNull(actorUserRole, "actorUserRole must not be null");
    }

    public StartForgotPasswordCommand(
            ForgotPasswordIdempotencyKey idempotencyKey, String username, String existingAuthFlowSessionId,
            String acceptLanguage, String correlationId, Duration idempotencyTtl) {
        this(idempotencyKey, username, existingAuthFlowSessionId, acceptLanguage, correlationId,
                idempotencyTtl, ActorUserRole.ROLE_USER_MBR);
    }

    @Override
    public String toString() {
        return "StartForgotPasswordCommand[idempotencyKey=<redacted>, username=<redacted>, existingAuthFlowSessionId=<redacted>, acceptLanguage="
                + acceptLanguage + ", correlationId=" + correlationId + ", idempotencyTtl=" + idempotencyTtl + "]";
    }
}