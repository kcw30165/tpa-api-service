package com.bct.ngtpa.apiservice.application.dto.auth.login;

/**
 * Credentials are transient and must never be included in logs or persisted.
 */
public record CredentialAuthenticationCommand(
        String username,
        String password,
        String acceptLanguage,
        String correlationId) {

    @Override
    public String toString() {
        return "CredentialAuthenticationCommand[username=<redacted>, password=<redacted>, "
                + "acceptLanguage=" + acceptLanguage + ", correlationId=" + correlationId + "]";
    }
}
