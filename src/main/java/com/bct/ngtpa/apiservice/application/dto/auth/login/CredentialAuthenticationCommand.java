package com.bct.ngtpa.apiservice.application.dto.auth.login;

import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import java.util.Objects;

/** Credentials are transient and must never be included in logs or persisted. */
public record CredentialAuthenticationCommand(
        String username,
        String password,
        String acceptLanguage,
        String correlationId,
        ActorUserRole actorUserRole) {

    public CredentialAuthenticationCommand {
        Objects.requireNonNull(actorUserRole, "actorUserRole must not be null");
    }

    @Override
    public String toString() {
        return "CredentialAuthenticationCommand[username=<redacted>, password=<redacted>, "
                + "acceptLanguage=" + acceptLanguage + ", correlationId=" + correlationId
                + ", actorUserRole=" + actorUserRole + "]";
    }
}
