package com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery;

import java.util.Objects;

import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;

/**
 * Application-level input for the Username Recovery cancel flow.
 *
 * <p>Design references: {@code docs/design/6-username-recovery/username-recovery-api-contract-design.md}
 * section 6 (cancel is bound to the {@code auth_flow_sid} session and is idempotent for a valid or
 * absent session) and {@code docs/design/6-username-recovery/username-recovery-session-redis-design.md}
 * section 6 (cookie-bound session).
 *
 * <p>Invariants: {@code authFlowSessionId} is required. {@link #toString()} never discloses the
 * session id.
 */
public record UsernameRecoveryCancelCommand(AuthFlowSessionId authFlowSessionId) {

    public UsernameRecoveryCancelCommand {
        Objects.requireNonNull(authFlowSessionId, "authFlowSessionId must not be null");
    }

    @Override
    public String toString() {
        return "UsernameRecoveryCancelCommand[authFlowSessionId=<redacted>]";
    }
}
