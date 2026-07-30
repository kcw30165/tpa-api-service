package com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency;

public record ForgotPasswordIdempotencyKey(String value) {
    public ForgotPasswordIdempotencyKey {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("ForgotPasswordIdempotencyKey must not be blank");
        }
        value = value.trim();
    }

    @Override
    public String toString() {
        return "ForgotPasswordIdempotencyKey[value=<redacted>]";
    }
}