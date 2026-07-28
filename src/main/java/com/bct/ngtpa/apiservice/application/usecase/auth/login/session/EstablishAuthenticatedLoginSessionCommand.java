package com.bct.ngtpa.apiservice.application.usecase.auth.login.session;

import com.bct.ngtpa.apiservice.application.dto.ActorContext;
import com.bct.ngtpa.apiservice.application.dto.auth.login.account.LoginSessionAccount;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import java.util.List;
import java.util.Objects;

public record EstablishAuthenticatedLoginSessionCommand(
        AuthFlowSessionId authFlowSessionId,
        ActorContext actorContext,
        List<LoginSessionAccount> accounts,
        String csrfTokenHash,
        String correlationId) {
    public EstablishAuthenticatedLoginSessionCommand {
        Objects.requireNonNull(authFlowSessionId, "authFlowSessionId must not be null");
        Objects.requireNonNull(actorContext, "actorContext must not be null");
        accounts = accounts == null ? List.of() : List.copyOf(accounts);
        if (accounts.isEmpty()) {
            throw new IllegalArgumentException("accounts must not be empty");
        }
        if (csrfTokenHash == null || csrfTokenHash.isBlank()) {
            throw new IllegalArgumentException("csrfTokenHash must not be blank");
        }
        csrfTokenHash = csrfTokenHash.trim();
        correlationId = correlationId == null || correlationId.isBlank() ? null : correlationId.trim();
    }

    @Override
    public String toString() {
        return "EstablishAuthenticatedLoginSessionCommand[authFlowSessionId=<redacted>, actor=<redacted>, accountCount="
                + accounts.size() + ", csrfTokenHash=<redacted>, correlationId=" + correlationId + "]";
    }
}
