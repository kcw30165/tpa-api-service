package com.bct.ngtpa.apiservice.application.dto.auth.login.otp.resend;

public record LoginOtpReplacementBindingCommand(String authFlowSessionId, String oldOtpChallengeId,
        String newOtpChallengeId, String correlationId) {
    @Override
    public String toString() {
        return "LoginOtpReplacementBindingCommand[authFlowSessionId=<redacted>, oldOtpChallengeId=<redacted>, newOtpChallengeId=<redacted>, correlationId="
                + correlationId + "]";
    }
}
