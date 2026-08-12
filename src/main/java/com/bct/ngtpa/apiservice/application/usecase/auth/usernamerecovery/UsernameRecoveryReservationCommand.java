package com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery;

import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySchemeRef;
import java.time.Instant;
import java.util.Objects;

/**
 * Application input for the Username Recovery candidate reservation boundary.
 *
 * <p>Model B decision {@code MODEL_B_GENERIC_ENVELOPE_TYPED_SIDECAR}: the canonical session
 * identifier is {@link AuthFlowSessionId}, the sole canonical identifier for both the generic
 * envelope and the workflow-state sidecar. The legacy {@code sessionId} component was replaced by
 * {@code authFlowSessionId}.
 *
 * <p>Transitional bridge (REMOVE_AFTER_025D): the deprecated constructor and {@link #sessionId()}
 * accessor keep the committed reservation service and Request orchestration compiling until
 * Repairs 025B/025C migrate them to the canonical identifier; no new callers may be added. Both
 * identifiers validate the same safe pattern, so the value maps 1:1 without loss.
 *
 * <p>Invariants: {@code authFlowSessionId}, {@code schemeRef}, and the authoritative current
 * instant {@code now} are all required. The raw CSRF token is deliberately not part of this
 * command: it is accepted only at the {@link UsernameRecoveryReservationService#reserve} method
 * boundary and is never stored in any record, field, log, exception, or {@code toString()}.
 * {@link #toString()} never discloses the session id or the opaque Scheme-Ref.
 */
public record UsernameRecoveryReservationCommand(
        AuthFlowSessionId authFlowSessionId,
        UsernameRecoverySchemeRef schemeRef,
        Instant now) {

    public UsernameRecoveryReservationCommand {
        Objects.requireNonNull(authFlowSessionId, "authFlowSessionId must not be null");
        Objects.requireNonNull(schemeRef, "schemeRef must not be null");
        Objects.requireNonNull(now, "now must not be null");
    }


    @Override
    public String toString() {
        return "UsernameRecoveryReservationCommand[authFlowSessionId=<redacted>, schemeRef=<redacted>, now="
                + now + "]";
    }
}
