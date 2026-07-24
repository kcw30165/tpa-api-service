package com.bct.ngtpa.apiservice.application.dto.auth.login.otp.resend;

public record LoginOtpResendContextCommand(String authFlowSessionId, String csrfToken, String origin, String referer,
        String correlationId) {
}
