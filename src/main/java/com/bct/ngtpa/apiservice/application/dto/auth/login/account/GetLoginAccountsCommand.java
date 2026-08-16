package com.bct.ngtpa.apiservice.application.dto.auth.login.account;

import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import java.util.Objects;

public record GetLoginAccountsCommand(String normalizedUsername, String custType, String correlationId,
        ActorUserRole actorUserRole) {
    public GetLoginAccountsCommand {
        normalizedUsername = required(normalizedUsername, "normalizedUsername");
        custType = required(custType, "custType");
        correlationId = correlationId == null || correlationId.isBlank() ? null : correlationId.trim();
        Objects.requireNonNull(actorUserRole, "actorUserRole must not be null");
    }
    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
    @Override public String toString() {
        return "GetLoginAccountsCommand[normalizedUsername=<redacted>, custType=" + custType
                + ", correlationId=" + correlationId + ", actorUserRole=" + actorUserRole + "]";
    }
}
