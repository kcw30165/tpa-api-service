package com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery;

import java.util.Objects;

import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySchemeRef;

/**
 * Application-level input for the Username Recovery request flow.
 *
 * <p>Design references: {@code docs/design/6-username-recovery/username-recovery-session-redis-design.md}
 * section 6 (the request is bound to the {@code auth_flow_sid} session) and
 * {@code docs/design/6-username-recovery/username-recovery-api-contract-design.md} section 5
 * (request targets a single session-bound opaque Scheme-Ref).
 *
 * <p>Invariants: both {@code authFlowSessionId} and {@code schemeRef} are required.
 * {@link #toString()} never discloses the session id or the opaque Scheme-Ref.
 */
public record UsernameRecoveryRequestCommand(
        AuthFlowSessionId authFlowSessionId,
        UsernameRecoverySchemeRef schemeRef) {

    public UsernameRecoveryRequestCommand {
        Objects.requireNonNull(authFlowSessionId, "authFlowSessionId must not be null");
        Objects.requireNonNull(schemeRef, "schemeRef must not be null");
    }

    @Override
    public String toString() {
        return "UsernameRecoveryRequestCommand[authFlowSessionId=<redacted>, schemeRef=<redacted>]";
    }
}
