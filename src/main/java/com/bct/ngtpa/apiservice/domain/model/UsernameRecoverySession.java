package com.bct.ngtpa.apiservice.domain.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Protected Username Recovery session aggregate.
 *
 * <p>Design references:
 *
 * <ul>
 *   <li>{@code docs/design/6-username-recovery/username-recovery-session-redis-design.md}
 *       sections 2-4 (lifecycle {@code ACTIVE -> PARTIALLY_REQUESTED -> COMPLETED};
 *       {@code ACTIVE/PARTIALLY_REQUESTED -> CANCELLED or EXPIRED}; session and candidate fields;
 *       completion counts registered candidates only; revision persisted consistently);</li>
 *   <li>{@code docs/design/6-username-recovery/username-recovery-solution-design.md}
 *       sections 6-7 (sender {@code ACCEPTED} maps the candidate to {@code REQUESTED}; requested
 *       timestamp and sender/idempotency reference required for replay safety);</li>
 *   <li>{@code docs/design/6-username-recovery/username-recovery-api-contract-design.md}
 *       section 5 (result data status {@code PARTIALLY_REQUESTED} / {@code COMPLETED}).</li>
 * </ul>
 *
 * <p>Invariants: session identity and auth-flow scenario are typed value objects, never raw
 * transport headers; workflow status is an approved Username Recovery status; {@code createdAt} and
 * {@code expiresAt} are required with expiry strictly after creation; revision is nonnegative and
 * advances by exactly one per approved mutation; CSRF storage retains a hash only, never the raw
 * token; verified destination and channel are protected and nonblank; candidates are immutable,
 * non-null, keyed by unique opaque Scheme-Ref with unique {@code accountEnv + trustCode} pairs; at
 * least one registered candidate is required; completion counts registered candidates only and the
 * session is {@code COMPLETED} only when every registered candidate is {@code REQUESTED};
 * unregistered candidates never block completion; completed, cancelled, and expired sessions reject
 * new delivery reservation or mutation. {@link #toString()} redacts session id, CSRF hash,
 * destination, username, Scheme-Ref, and sender references.
 */
public record UsernameRecoverySession(
        UsernameRecoverySessionId sessionId,
        Scenario scenario,
        UsernameRecoveryWorkflowStatus status,
        UsernameRecoverySessionRevision revision,
        UsernameRecoveryCsrfHash csrfHash,
        UsernameRecoveryVerifiedDestination destination,
        Locale language,
        Instant createdAt,
        Instant expiresAt,
        List<UsernameRecoverySessionCandidate> candidates) {

    public enum Scenario {
        USERNAME_RECOVERY
    }

    private record EnvTrustKey(String accountEnv, String trustCode) {}

    public UsernameRecoverySession {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(scenario, "scenario must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(revision, "revision must not be null");
        Objects.requireNonNull(csrfHash, "csrfHash must not be null");
        Objects.requireNonNull(destination, "destination must not be null");
        Objects.requireNonNull(language, "language must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("expiresAt must be strictly after createdAt");
        }
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("session requires at least one candidate");
        }
        if (candidates.stream().noneMatch(UsernameRecoverySessionCandidate::registered)) {
            throw new IllegalArgumentException("session requires at least one registered candidate");
        }
        if (candidates.stream().map(UsernameRecoverySessionCandidate::schemeRef).distinct().count()
                != candidates.size()) {
            throw new IllegalArgumentException("duplicate schemeRef is rejected");
        }
        if (candidates.stream()
                .map(c -> new EnvTrustKey(c.accountEnv(), c.trustCode()))
                .distinct()
                .count() != candidates.size()) {
            throw new IllegalArgumentException("duplicate accountEnv+trustCode candidate is rejected");
        }
        boolean allRegisteredRequested = candidates.stream()
                .filter(UsernameRecoverySessionCandidate::registered)
                .allMatch(c -> c.deliveryState() == UsernameRecoveryDeliveryState.REQUESTED);
        if (status == UsernameRecoveryWorkflowStatus.COMPLETED && !allRegisteredRequested) {
            throw new IllegalArgumentException("COMPLETED requires every registered candidate to be REQUESTED");
        }
        if (allRegisteredRequested && status != UsernameRecoveryWorkflowStatus.COMPLETED) {
            throw new IllegalArgumentException("every registered candidate REQUESTED requires COMPLETED status");
        }
    }

    public boolean isExpired(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        return !now.isBefore(expiresAt);
    }

    public boolean isTerminal() {
        return status == UsernameRecoveryWorkflowStatus.COMPLETED
                || status == UsernameRecoveryWorkflowStatus.CANCELLED
                || status == UsernameRecoveryWorkflowStatus.EXPIRED;
    }

    public UsernameRecoverySession reserve(UsernameRecoverySchemeRef schemeRef) {
        Objects.requireNonNull(schemeRef, "schemeRef must not be null");
        requireOpenForMutation();
        int index = indexOf(schemeRef);
        UsernameRecoverySessionCandidate current = candidates.get(index);
        requireRegistered(current);
        if (current.deliveryState() != UsernameRecoveryDeliveryState.AVAILABLE) {
            throw new IllegalStateException(
                    "candidate is not available for reservation: " + current.deliveryState());
        }
        return withCandidate(
                index,
                current.withState(UsernameRecoveryDeliveryState.RESERVED, null, null),
                status);
    }

    public UsernameRecoverySession markRequested(
            UsernameRecoverySchemeRef schemeRef, String senderReference, Instant requestedAt) {
        Objects.requireNonNull(schemeRef, "schemeRef must not be null");
        Objects.requireNonNull(senderReference, "senderReference must not be null");
        Objects.requireNonNull(requestedAt, "requestedAt must not be null");
        requireOpenForMutation();
        int index = indexOf(schemeRef);
        UsernameRecoverySessionCandidate current = candidates.get(index);
        requireRegistered(current);
        if (current.deliveryState() != UsernameRecoveryDeliveryState.RESERVED) {
            throw new IllegalStateException(
                    "candidate must be reserved before requesting: " + current.deliveryState());
        }
        UsernameRecoverySessionCandidate requested =
                current.withState(UsernameRecoveryDeliveryState.REQUESTED, requestedAt, senderReference);
        List<UsernameRecoverySessionCandidate> nextCandidates = replace(index, requested);
        boolean allRegisteredRequested = nextCandidates.stream()
                .filter(UsernameRecoverySessionCandidate::registered)
                .allMatch(c -> c.deliveryState() == UsernameRecoveryDeliveryState.REQUESTED);
        UsernameRecoveryWorkflowStatus nextStatus = allRegisteredRequested
                ? UsernameRecoveryWorkflowStatus.COMPLETED
                : UsernameRecoveryWorkflowStatus.PARTIALLY_REQUESTED;
        return withCandidates(nextCandidates, nextStatus);
    }

    public UsernameRecoverySession markFailed(UsernameRecoverySchemeRef schemeRef) {
        Objects.requireNonNull(schemeRef, "schemeRef must not be null");
        requireOpenForMutation();
        int index = indexOf(schemeRef);
        UsernameRecoverySessionCandidate current = candidates.get(index);
        requireRegistered(current);
        if (current.deliveryState() != UsernameRecoveryDeliveryState.RESERVED) {
            throw new IllegalStateException(
                    "candidate must be reserved before failing: " + current.deliveryState());
        }
        return withCandidate(
                index,
                current.withState(UsernameRecoveryDeliveryState.FAILED, null, null),
                status);
    }

    public UsernameRecoverySession markUnknownOutcome(UsernameRecoverySchemeRef schemeRef) {
        Objects.requireNonNull(schemeRef, "schemeRef must not be null");
        requireOpenForMutation();
        int index = indexOf(schemeRef);
        UsernameRecoverySessionCandidate current = candidates.get(index);
        requireRegistered(current);
        if (current.deliveryState() != UsernameRecoveryDeliveryState.RESERVED) {
            throw new IllegalStateException(
                    "candidate must be reserved before recording an unknown outcome: "
                            + current.deliveryState());
        }
        return withCandidate(
                index,
                current.withState(UsernameRecoveryDeliveryState.UNKNOWN_OUTCOME, null, null),
                status);
    }

    public UsernameRecoverySession cancel() {
        requireOpenForMutation();
        return withStatus(UsernameRecoveryWorkflowStatus.CANCELLED);
    }

    public UsernameRecoverySession expire() {
        requireOpenForMutation();
        return withStatus(UsernameRecoveryWorkflowStatus.EXPIRED);
    }

    private void requireOpenForMutation() {
        if (status != UsernameRecoveryWorkflowStatus.ACTIVE
                && status != UsernameRecoveryWorkflowStatus.PARTIALLY_REQUESTED) {
            throw new IllegalStateException("session is terminal: " + status);
        }
    }

    private void requireRegistered(UsernameRecoverySessionCandidate candidate) {
        if (!candidate.registered()) {
            throw new IllegalStateException(
                    "unregistered candidate cannot participate in delivery transitions");
        }
    }

    private int indexOf(UsernameRecoverySchemeRef schemeRef) {
        for (int i = 0; i < candidates.size(); i++) {
            if (candidates.get(i).schemeRef().equals(schemeRef)) {
                return i;
            }
        }
        throw new IllegalArgumentException("unknown schemeRef");
    }

    private List<UsernameRecoverySessionCandidate> replace(
            int index, UsernameRecoverySessionCandidate updated) {
        List<UsernameRecoverySessionCandidate> next = new ArrayList<>(candidates);
        next.set(index, updated);
        return next;
    }

    private UsernameRecoverySession withCandidate(
            int index, UsernameRecoverySessionCandidate updated, UsernameRecoveryWorkflowStatus nextStatus) {
        return withCandidates(replace(index, updated), nextStatus);
    }

    private UsernameRecoverySession withCandidates(
            List<UsernameRecoverySessionCandidate> nextCandidates, UsernameRecoveryWorkflowStatus nextStatus) {
        return new UsernameRecoverySession(sessionId, scenario, nextStatus, revision.next(), csrfHash,
                destination, language, createdAt, expiresAt, List.copyOf(nextCandidates));
    }

    private UsernameRecoverySession withStatus(UsernameRecoveryWorkflowStatus nextStatus) {
        return new UsernameRecoverySession(sessionId, scenario, nextStatus, revision.next(), csrfHash,
                destination, language, createdAt, expiresAt, candidates);
    }

    @Override
    public String toString() {
        return "UsernameRecoverySession[sessionId=<redacted>, scenario=" + scenario
                + ", status=" + status + ", revision=" + revision.value()
                + ", csrfHash=<redacted>, destination=<redacted>"
                + ", language=" + language + ", createdAt=" + createdAt
                + ", expiresAt=" + expiresAt + ", candidateCount=" + candidates.size() + "]";
    }
}
