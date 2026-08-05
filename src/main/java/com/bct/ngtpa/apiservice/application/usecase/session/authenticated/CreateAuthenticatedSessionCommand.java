package com.bct.ngtpa.apiservice.application.usecase.session.authenticated;

import com.bct.ngtpa.apiservice.application.dto.ActorContext;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionAccount;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import java.util.*;

public record CreateAuthenticatedSessionCommand(ActorContext actorContext,
        List<AuthenticatedSessionAccount> accounts,
        String csrfTokenHash,
        String correlationId,
        AuthenticatedSessionId requestedSessionId) {
    public CreateAuthenticatedSessionCommand {
        Objects.requireNonNull(actorContext);
        accounts = accounts == null ? List.of() : List.copyOf(accounts);
        if (csrfTokenHash == null || csrfTokenHash.isBlank())
            throw new IllegalArgumentException("csrfTokenHash required");
        if (accounts.isEmpty())
            throw new IllegalArgumentException("accounts required");
        correlationId = correlationId == null || correlationId.isBlank() ? null : correlationId.trim();
    }

    @Override
    public String toString() {
        return "CreateAuthenticatedSessionCommand[actor=<redacted>, accountCount=" + accounts.size()
                + ", csrfTokenHash=<redacted>, correlationId=" + correlationId + "]";
    }

    public CreateAuthenticatedSessionCommand(ActorContext actorContext,
            List<AuthenticatedSessionAccount> accounts,
            String csrfTokenHash,
            String correlationId) {
        this(actorContext, accounts, csrfTokenHash, correlationId, null);
    }
}
