package com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery;

import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySchemeRef;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySessionId;
import java.time.Instant;
import java.util.Objects;

/**
 * Application input for the Username Recovery candidate reservation boundary.
 *
 * <p>Design references: {@code docs/design/6-username-recovery/username-recovery-api-contract-design.md}
 * section 5 (the request targets a single session-bound opaque Scheme-Ref) and section 8
 * (UR-REQ-001 missing/invalid/expired session, UR-REQ-002 CSRF, UR-REQ-003 Scheme-Ref) and
 * {@code docs/design/6-username-recovery/username-recovery-session-redis-design.md} section 6
 * (the request is bound to the auth-flow session).
 *
 * <p>Invariants: {@code sessionId}, {@code schemeRef}, and the authoritative current instant
 * {@code now} are all required. The raw CSRF token is deliberately not part of this command: it is
 * accepted only at the {@link UsernameRecoveryReservationService#reserve} method boundary and is
 * never stored in any record, field, log, exception, or {@code toString()}.
 * {@link #toString()} never discloses the session id or the opaque Scheme-Ref.
 */
public record UsernameRecoveryReservationCommand(
        UsernameRecoverySessionId sessionId,
        UsernameRecoverySchemeRef schemeRef,
        Instant now) {

    public UsernameRecoveryReservationCommand {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(schemeRef, "schemeRef must not be null");
        Objects.requireNonNull(now, "now must not be null");
    }

    @Override
    public String toString() {
        return "UsernameRecoveryReservationCommand[sessionId=<redacted>, schemeRef=<redacted>, now="
                + now + "]";
    }
}
