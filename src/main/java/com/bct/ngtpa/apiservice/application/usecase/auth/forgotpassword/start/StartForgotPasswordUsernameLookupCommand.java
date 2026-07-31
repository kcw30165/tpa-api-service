package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.start;

import java.util.Objects;

public record StartForgotPasswordUsernameLookupCommand(
        String normalizedUsername,
        String correlationId) {
    public StartForgotPasswordUsernameLookupCommand {
        if (normalizedUsername == null || normalizedUsername.isBlank()) {
            throw new IllegalArgumentException("normalizedUsername must not be blank");
        }
        Objects.requireNonNull(correlationId, "correlationId must not be null");
        normalizedUsername = normalizedUsername.trim();
    }

    @Override
    public String toString() {
        return "StartForgotPasswordUsernameLookupCommand[normalizedUsername=<redacted>, correlationId="
                + correlationId + "]";
    }
}