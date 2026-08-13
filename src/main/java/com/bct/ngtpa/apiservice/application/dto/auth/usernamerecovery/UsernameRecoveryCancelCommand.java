package com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery;

import java.util.Objects;

import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;

/**
 * Application-level input for the Username Recovery cancel flow.
 *
 * <p>Design references: {@code docs/design/6-username-recovery/username-recovery-api-contract-design.md}
 * section 6 (cancel is bound to the {@code auth_flow_sid} session and is idempotent for a valid or
 * absent session; the {@code Idempotency-Key} header is required) and
 * {@code docs/design/6-username-recovery/username-recovery-session-redis-design.md} section 6
 * (cookie-bound session).
 *
 * <p>Repair 018A: the raw client-provided {@code Idempotency-Key} enters through this typed command.
 * {@code idempotencyKey} is the opaque client key, distinct from the semantic fingerprint; later
 * use cases may convert it into {@code UsernameRecoveryIdempotencyKey}. No raw CSRF is carried.
 *
 * <p>Invariants: {@code authFlowSessionId} and {@code idempotencyKey} are required; the idempotency
 * key is trimmed and nonblank (its case is preserved). {@link #toString()} never discloses the
 * session id or the idempotency key.
 */
public record UsernameRecoveryCancelCommand(AuthFlowSessionId authFlowSessionId, String idempotencyKey) {

    public UsernameRecoveryCancelCommand {
        Objects.requireNonNull(authFlowSessionId, "authFlowSessionId must not be null");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
        idempotencyKey = idempotencyKey.trim();
        if (idempotencyKey.isBlank()) {
            throw new IllegalArgumentException(
                    "username recovery cancel command idempotencyKey must not be blank");
        }
    }

    @Override
    public String toString() {
        return "UsernameRecoveryCancelCommand[authFlowSessionId=<redacted>, idempotencyKey=<redacted>]";
    }
}
