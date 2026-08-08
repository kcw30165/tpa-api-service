package com.bct.ngtpa.apiservice.application.port.out;

/**
 * Deterministic reserve outcomes for the Username Recovery idempotency port.
 *
 * <p>Design references: {@code docs/design/6-username-recovery/username-recovery-session-redis-design.md}
 * section 4 (reservation ownership/idempotency operation) and section 8 (UNKNOWN_OUTCOME is never
 * blindly resent; infrastructure failures are never treated as success). The matrix is recorded in
 * evidence {@code 004-outcome-matrix.txt}.
 */
public enum UsernameRecoveryIdempotencyOutcome {

    /** The caller acquired the only reservation for this action/key. */
    RESERVED,

    /** Another in-progress reservation already owns this action/key; at most one owner. */
    DUPLICATE_IN_PROGRESS,

    /** Same key and same semantic fingerprint; the committed result may be replayed via resolve. */
    COMPLETED_REPLAY,

    /** Same key but a different semantic fingerprint; the caller must surface a key-reused conflict. */
    KEY_REUSED_WITH_DIFFERENT_REQUEST,

    /** A stable unknown-outcome record exists for this key; never blind-resend. */
    UNKNOWN_OUTCOME_EXISTING,

    /** Infrastructure failure; never a successful reservation or replay result. */
    SYSTEM_FAILURE
}
