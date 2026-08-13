package com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery;

/**
 * Typed outcomes of the Username Recovery candidate reservation boundary.
 *
 * <p>Design references: {@code docs/design/6-username-recovery/username-recovery-api-contract-design.md}
 * section 8 (UR-REQ-001..011) and section 7 (HTTP mapping rules) and
 * {@code .cline/tasks/015-csrf-session-integration.md} "Typed outcome expectations" (the bounded
 * result must distinguish reserved/applied, absent/invalid, expired, CSRF invalid, Scheme-Ref
 * invalid, registration required, already requested, terminal, revision conflict, and system
 * failure).
 *
 * <p>Each expected workflow outcome is a typed constant; no null, empty {@code Mono}, exception,
 * or arbitrary string is used for expected outcomes.
 */
public enum UsernameRecoveryReservationOutcome {
    /** The eligible registered candidate was reserved and the CAS mutation was applied. */
    RESERVED,
    /** The session is absent or invalid (load or update reported no session). */
    SESSION_ABSENT,
    /** The loaded session is past its authoritative expiry and must not be mutated. */
    SESSION_EXPIRED,
    /** The presented CSRF token is missing or does not match the stored hash. */
    CSRF_INVALID,
    /** The presented opaque Scheme-Ref is not owned by the loaded session. */
    SCHEME_REF_INVALID,
    /** The candidate exists but is not registered and cannot be reserved. */
    REGISTRATION_REQUIRED,
    /** The candidate has already been requested and must not be reserved again. */
    ALREADY_REQUESTED,
    /** The candidate is otherwise ineligible (reserved, failed, or unknown outcome). */
    CANDIDATE_NOT_AVAILABLE,
    /** The session workflow is completed, cancelled, or otherwise terminal. */
    WORKFLOW_TERMINAL,
    /** The expected-revision compare-and-set conflicted with a concurrent mutation. */
    REVISION_CONFLICT,
    /** A system or infrastructure failure occurred; no success is implied. */
    SYSTEM_FAILED
}
