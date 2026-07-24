package com.bct.ngtpa.apiservice.application.dto.auth.login.otp.resend;

public record ResendLoginOtpCommand(String authFlowSessionId, String csrfToken, String idempotencyKey, String origin,
        String referer, String acceptLanguage, String correlationId) {
    public ResendLoginOtpCommand {
        if (authFlowSessionId == null || authFlowSessionId.isBlank() || csrfToken == null || csrfToken.isBlank()
                || idempotencyKey == null || idempotencyKey.isBlank())
            throw new IllegalArgumentException("Required resend security context missing");
    }

    @Override
    public String toString() {
        return "ResendLoginOtpCommand[authFlowSessionId=<redacted>, csrfToken=<redacted>, idempotencyKey=<redacted>, origin=<redacted>, referer=<redacted>, acceptLanguage="
                + acceptLanguage + ", correlationId=" + correlationId + "]";
    }
}
