package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.complete;

import java.util.Objects;

public record ForgotPasswordCompletionCommand(String customerId, String password, String correlationId) {
    public ForgotPasswordCompletionCommand {
        customerId = required(customerId, "customerId");
        password = required(password, "password");
        correlationId = correlationId == null || correlationId.isBlank() ? null : correlationId.trim();
    }

    private static String required(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    @Override
    public String toString() {
        return "ForgotPasswordCompletionCommand[customerId=<redacted>, password=<redacted>, correlationId="
                + correlationId + "]";
    }
}