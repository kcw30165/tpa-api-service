package com.bct.ngtpa.apiservice.application.usecase.auth.login.otp;

public record SendLoginOtpCommand(
        String authFlowSessionId,
        String csrfToken,
        String destinationId,
        String idempotencyKey,
        String origin,
        String referer,
        String acceptLanguage,
        String correlationId) {
    public SendLoginOtpCommand {
        authFlowSessionId = required(authFlowSessionId, "authFlowSessionId");
        csrfToken = required(csrfToken, "csrfToken");
        destinationId = required(destinationId, "destinationId");
        idempotencyKey = required(idempotencyKey, "idempotencyKey");
        origin = optional(origin); referer = optional(referer);
        acceptLanguage = optional(acceptLanguage); correlationId = optional(correlationId);
    }
    private static String required(String value,String name){if(value==null||value.isBlank())throw new IllegalArgumentException(name+" must not be blank");return value.trim();}
    private static String optional(String value){return value==null||value.isBlank()?null:value.trim();}
    @Override public String toString(){return "SendLoginOtpCommand[authFlowSessionId=<redacted>, csrfToken=<redacted>, destinationId=<redacted>, idempotencyKey=<redacted>, origin=<redacted>, referer=<redacted>, acceptLanguage="+acceptLanguage+", correlationId="+correlationId+"]";}
}
