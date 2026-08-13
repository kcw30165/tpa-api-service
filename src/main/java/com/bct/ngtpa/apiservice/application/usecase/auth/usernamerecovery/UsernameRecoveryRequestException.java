package com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery;

import java.util.Objects;

/**
 * Typed Task 020 request rejection signal for every distinct non-result condition of the Username
 * Recovery request flow.
 *
 * <p>Design references: {@code docs/design/6-username-recovery/username-recovery-api-contract-design.md}
 * sections 7-8 (UR-REQ-001..011 HTTP/status mapping) and section 9 (no silent exceptions; unexpected
 * defects reach the central exception boundary). The committed inbound port returns only
 * {@code Mono<UsernameRecoveryRequestResult>}; conditions the result cannot represent - validation
 * failure, a missing security context, idempotency in-progress / key-reused / unknown-outcome-existing,
 * absent or expired session, CSRF/origin failure, candidate not available, revision conflict, and
 * definite system failure - are expressed as typed {@link Reason} signals the future web layer can
 * map distinctly.
 *
 * <p>Invariants: {@code reason} is required and the message never contains raw exception text,
 * session ids, Scheme-Refs, CSRF values, destinations, usernames, idempotency keys, fingerprints,
 * or operation references. {@link #toString()} is fully redacted.
 */
public final class UsernameRecoveryRequestException extends RuntimeException {

    private final Reason reason;

    public enum Reason {
        VALIDATION_FAILED,
        SECURITY_CONTEXT_REQUIRED,
        IDEMPOTENCY_IN_PROGRESS,
        IDEMPOTENCY_KEY_REUSED,
        UNKNOWN_OUTCOME_EXISTING,
        SESSION_ABSENT,
        SESSION_EXPIRED,
        SECURITY_INVALID,
        CANDIDATE_NOT_AVAILABLE,
        REVISION_CONFLICT,
        SYSTEM_FAILURE
    }

    private UsernameRecoveryRequestException(Reason reason, Throwable cause) {
        super("Username recovery request rejected: " + Objects.requireNonNull(reason).name(), cause);
        this.reason = reason;
    }

    public static UsernameRecoveryRequestException validationFailed() {
        return new UsernameRecoveryRequestException(Reason.VALIDATION_FAILED, null);
    }

    public static UsernameRecoveryRequestException securityContextRequired() {
        return new UsernameRecoveryRequestException(Reason.SECURITY_CONTEXT_REQUIRED, null);
    }

    public static UsernameRecoveryRequestException idempotencyInProgress() {
        return new UsernameRecoveryRequestException(Reason.IDEMPOTENCY_IN_PROGRESS, null);
    }

    public static UsernameRecoveryRequestException keyReused() {
        return new UsernameRecoveryRequestException(Reason.IDEMPOTENCY_KEY_REUSED, null);
    }

    public static UsernameRecoveryRequestException unknownOutcomeExisting() {
        return new UsernameRecoveryRequestException(Reason.UNKNOWN_OUTCOME_EXISTING, null);
    }

    public static UsernameRecoveryRequestException sessionAbsent() {
        return new UsernameRecoveryRequestException(Reason.SESSION_ABSENT, null);
    }

    public static UsernameRecoveryRequestException sessionExpired() {
        return new UsernameRecoveryRequestException(Reason.SESSION_EXPIRED, null);
    }

    public static UsernameRecoveryRequestException securityInvalid() {
        return new UsernameRecoveryRequestException(Reason.SECURITY_INVALID, null);
    }

    public static UsernameRecoveryRequestException candidateNotAvailable() {
        return new UsernameRecoveryRequestException(Reason.CANDIDATE_NOT_AVAILABLE, null);
    }

    public static UsernameRecoveryRequestException revisionConflict() {
        return new UsernameRecoveryRequestException(Reason.REVISION_CONFLICT, null);
    }

    public static UsernameRecoveryRequestException systemFailure() {
        return new UsernameRecoveryRequestException(Reason.SYSTEM_FAILURE, null);
    }

    public static UsernameRecoveryRequestException systemFailure(Throwable cause) {
        return new UsernameRecoveryRequestException(
                Reason.SYSTEM_FAILURE, Objects.requireNonNull(cause, "cause must not be null"));
    }

    public Reason reason() {
        return reason;
    }

    @Override
    public String toString() {
        return "UsernameRecoveryRequestException[reason=" + reason + "]";
    }
}
