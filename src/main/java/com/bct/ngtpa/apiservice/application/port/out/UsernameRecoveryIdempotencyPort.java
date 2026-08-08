package com.bct.ngtpa.apiservice.application.port.out;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import reactor.core.publisher.Mono;

/**
 * Application outbound port for Username Recovery idempotency persistence.
 *
 * <p>Exposes application/domain contracts only: no Redis, Spring Data, HTTP, cookie, APIM, web,
 * serialization, or Lua types. The persistence adapter (Task 014) implements reservation,
 * resolve/replay, commit, and stable unknown-outcome operations against Redis.
 *
 * <p>Design references: {@code docs/design/6-username-recovery/username-recovery-session-redis-design.md}
 * section 4 (reservation ownership, replay snapshot, {@code UNKNOWN_OUTCOME}) and section 8
 * (no silent catch/discard; infrastructure failures are never successful reservations or replays).
 * The reserve outcome matrix is recorded in evidence {@code 004-outcome-matrix.txt}.
 *
 * <p>No-blind-resend guarantee: {@code UNKNOWN_OUTCOME} records never carry a replay snapshot, and
 * a reserve against an unknown-outcome key returns {@code UNKNOWN_OUTCOME_EXISTING} instead of
 * permitting a new send. Same key plus same semantic fingerprint may replay the committed result
 * through {@code COMPLETED_REPLAY} + {@link #resolve}; same key plus a different fingerprint
 * returns {@code KEY_REUSED_WITH_DIFFERENT_REQUEST}.
 */
public interface UsernameRecoveryIdempotencyPort {

    /**
     * Atomically reserves the action/key for exactly one caller.
     *
     * <p>Outcomes: {@link UsernameRecoveryIdempotencyOutcome#RESERVED} (the caller owns the
     * reservation), {@code DUPLICATE_IN_PROGRESS}, {@code COMPLETED_REPLAY},
     * {@code KEY_REUSED_WITH_DIFFERENT_REQUEST}, {@code UNKNOWN_OUTCOME_EXISTING}, or
     * {@code SYSTEM_FAILURE}. Infrastructure exceptions must never be represented as a successful
     * reservation or replay result.
     */
    Mono<ReservationResult> reserve(ReserveCommand command);

    /**
     * Resolves the current record for an action/key without mutating it.
     *
     * <p>An empty result means the key has no record (fresh key). A present record is
     * {@code IN_PROGRESS}, {@code COMPLETED} (replay snapshot available), or
     * {@code UNKNOWN_OUTCOME}. Infrastructure failure must not be returned as an empty result.
     */
    Mono<Optional<UsernameRecoveryIdempotencyRecord>> resolve(
            UsernameRecoveryIdempotencyAction action,
            UsernameRecoveryIdempotencyKey idempotencyKey);

    /**
     * Commits the completed replay snapshot for the reservation owned by the caller.
     *
     * <p>The command must present the same operation reference as the successful reservation.
     * Returns the completed record.
     */
    Mono<UsernameRecoveryIdempotencyRecord> complete(CompleteCommand command);

    /**
     * Persists the stable unknown outcome for the reservation owned by the caller.
     *
     * <p>The command must present the same operation reference as the successful reservation.
     * No replay snapshot is stored; the outcome is stable and never blindly resent.
     * Returns the unknown-outcome record.
     */
    Mono<UsernameRecoveryIdempotencyRecord> recordUnknownOutcome(UnknownOutcomeCommand command);

    /** Reserve command: opaque key, semantic fingerprint, and the caller's ownership claim. */
    record ReserveCommand(
            UsernameRecoveryIdempotencyAction action,
            UsernameRecoveryIdempotencyKey idempotencyKey,
            UsernameRecoveryIdempotencyFingerprint fingerprint,
            UsernameRecoveryIdempotencyOperationReference operationReference,
            Instant now,
            Duration ttl) {

        public ReserveCommand {
            Objects.requireNonNull(action, "action must not be null");
            Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
            Objects.requireNonNull(fingerprint, "fingerprint must not be null");
            Objects.requireNonNull(operationReference, "operationReference must not be null");
            Objects.requireNonNull(now, "now must not be null");
            Objects.requireNonNull(ttl, "ttl must not be null");
            if (!ttl.isPositive()) {
                throw new IllegalArgumentException("ttl must be positive");
            }
        }

        /** The reservation/record expiry instant computed from {@code now} and {@code ttl}. */
        public Instant expiresAt() {
            return now.plus(ttl);
        }

        @Override
        public String toString() {
            return "ReserveCommand[action=" + action + ", now=" + now + ", ttl=" + ttl
                    + ", idempotencyKey=<redacted>, fingerprint=<redacted>, operationReference=<redacted>]";
        }
    }

    /**
     * Reserve result. Only {@link UsernameRecoveryIdempotencyOutcome#RESERVED} carries a
     * {@link UsernameRecoveryIdempotencyReservation}; every other outcome, including
     * {@code SYSTEM_FAILURE}, must not carry one.
     */
    record ReservationResult(
            UsernameRecoveryIdempotencyOutcome outcome,
            UsernameRecoveryIdempotencyReservation reservation) {

        public ReservationResult {
            Objects.requireNonNull(outcome, "outcome must not be null");
            boolean reserved = outcome == UsernameRecoveryIdempotencyOutcome.RESERVED;
            if (reserved && reservation == null) {
                throw new IllegalArgumentException("reserved outcome requires a reservation");
            }
            if (!reserved && reservation != null) {
                throw new IllegalArgumentException("non-reserved outcome must not carry a reservation");
            }
        }

        @Override
        public String toString() {
            return "ReservationResult[outcome=" + outcome
                    + ", reservationPresent=" + (reservation != null) + "]";
        }
    }

    /** Commit command: ownership-checked completion with the safe replay snapshot. */
    record CompleteCommand(
            UsernameRecoveryIdempotencyAction action,
            UsernameRecoveryIdempotencyKey idempotencyKey,
            UsernameRecoveryIdempotencyFingerprint fingerprint,
            UsernameRecoveryIdempotencyOperationReference operationReference,
            UsernameRecoveryIdempotencyRecord.ReplaySnapshot replaySnapshot,
            Instant now,
            Duration ttl) {

        public CompleteCommand {
            Objects.requireNonNull(action, "action must not be null");
            Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
            Objects.requireNonNull(fingerprint, "fingerprint must not be null");
            Objects.requireNonNull(operationReference, "operationReference must not be null");
            Objects.requireNonNull(replaySnapshot, "replaySnapshot must not be null");
            Objects.requireNonNull(now, "now must not be null");
            Objects.requireNonNull(ttl, "ttl must not be null");
            if (!ttl.isPositive()) {
                throw new IllegalArgumentException("ttl must be positive");
            }
        }

        @Override
        public String toString() {
            return "CompleteCommand[action=" + action + ", now=" + now + ", ttl=" + ttl
                    + ", idempotencyKey=<redacted>, fingerprint=<redacted>, operationReference=<redacted>"
                    + ", replaySnapshot=<redacted>]";
        }
    }

    /** Unknown-outcome command: ownership-checked stable unknown-outcome recording. */
    record UnknownOutcomeCommand(
            UsernameRecoveryIdempotencyAction action,
            UsernameRecoveryIdempotencyKey idempotencyKey,
            UsernameRecoveryIdempotencyFingerprint fingerprint,
            UsernameRecoveryIdempotencyOperationReference operationReference,
            Instant now,
            Duration ttl) {

        public UnknownOutcomeCommand {
            Objects.requireNonNull(action, "action must not be null");
            Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
            Objects.requireNonNull(fingerprint, "fingerprint must not be null");
            Objects.requireNonNull(operationReference, "operationReference must not be null");
            Objects.requireNonNull(now, "now must not be null");
            Objects.requireNonNull(ttl, "ttl must not be null");
            if (!ttl.isPositive()) {
                throw new IllegalArgumentException("ttl must be positive");
            }
        }

        @Override
        public String toString() {
            return "UnknownOutcomeCommand[action=" + action + ", now=" + now + ", ttl=" + ttl
                    + ", idempotencyKey=<redacted>, fingerprint=<redacted>, operationReference=<redacted>]";
        }
    }
}
