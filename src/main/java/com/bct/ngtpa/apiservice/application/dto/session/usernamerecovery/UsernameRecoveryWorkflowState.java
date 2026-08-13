package com.bct.ngtpa.apiservice.application.dto.session.usernamerecovery;

import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryDeliveryState;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySchemeRef;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySessionCandidate;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryVerifiedDestination;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryWorkflowStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Protected Username Recovery workflow-state sidecar aggregate under the Model B decision
 * {@code MODEL_B_GENERIC_ENVELOPE_TYPED_SIDECAR}.
 *
 * <p>Ownership split (audit evidence 003):
 *
 * <ul>
 *   <li>Generic envelope ({@code AuthFlowSession}): authoritative identity, lifecycle status,
 *       CSRF, expiry, cancellation, deletion, generic revision, Redis envelope, and TTL.</li>
 *   <li>This sidecar: feature-specific language, verified destination, candidates, delivery
 *       transitions, workflow projection, and workflow-state revision.</li>
 * </ul>
 *
 * <p>Canonical identity: {@link AuthFlowSessionId} is the sole canonical identifier for both the
 * generic envelope and this sidecar. The sidecar deliberately contains no scenario, CSRF hash,
 * created/updated/expiry timestamps, cookie data, or generic lifecycle revision, and exposes only feature-owned lifecycle projection behavior such as {@link #markCancelled()}.
 *
 * <p>Invariants: non-null canonical {@code authFlowSessionId}; non-null status, revision,
 * destination, language, and immutable candidates; at least one candidate; at least one registered
 * candidate; unique Scheme-Refs; unique {@code accountEnv + trustCode} pairs; only registered
 * candidates participate in delivery transitions; reservation requires {@code AVAILABLE};
 * requested/failed/unknown-outcome transitions require {@code RESERVED}; all registered candidates
 * requested implies the {@code COMPLETED} projection and {@code COMPLETED} requires every
 * registered candidate requested; unregistered candidates never block completion.
 * {@link #toString()} redacts the session id, destination, usernames, Scheme-Refs, and sender
 * references.
 */
public record UsernameRecoveryWorkflowState(
        AuthFlowSessionId authFlowSessionId,
        UsernameRecoveryWorkflowStatus status,
        UsernameRecoveryWorkflowStateRevision revision,
        UsernameRecoveryVerifiedDestination destination,
        Locale language,
        List<UsernameRecoverySessionCandidate> candidates) {

    private record EnvTrustKey(String accountEnv, String trustCode) {}

    public UsernameRecoveryWorkflowState {
        Objects.requireNonNull(authFlowSessionId, "authFlowSessionId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(revision, "revision must not be null");
        Objects.requireNonNull(destination, "destination must not be null");
        Objects.requireNonNull(language, "language must not be null");
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("workflow state requires at least one candidate");
        }
        if (candidates.stream().noneMatch(UsernameRecoverySessionCandidate::registered)) {
            throw new IllegalArgumentException(
                    "workflow state requires at least one registered candidate");
        }
        if (candidates.stream().map(UsernameRecoverySessionCandidate::schemeRef).distinct().count()
                != candidates.size()) {
            throw new IllegalArgumentException("duplicate schemeRef is rejected");
        }
        if (candidates.stream()
                .map(c -> new EnvTrustKey(c.accountEnv(), c.trustCode()))
                .distinct()
                .count() != candidates.size()) {
            throw new IllegalArgumentException(
                    "duplicate accountEnv+trustCode candidate is rejected");
        }
        boolean allRegisteredRequested = candidates.stream()
                .filter(UsernameRecoverySessionCandidate::registered)
                .allMatch(c -> c.deliveryState() == UsernameRecoveryDeliveryState.REQUESTED);
        if (status == UsernameRecoveryWorkflowStatus.COMPLETED && !allRegisteredRequested) {
            throw new IllegalArgumentException(
                    "COMPLETED requires every registered candidate to be REQUESTED");
        }
        if (allRegisteredRequested && status != UsernameRecoveryWorkflowStatus.COMPLETED) {
            throw new IllegalArgumentException(
                    "every registered candidate REQUESTED requires COMPLETED status");
        }
    }

    public UsernameRecoveryWorkflowState reserve(UsernameRecoverySchemeRef schemeRef) {
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

    public UsernameRecoveryWorkflowState markRequested(
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
                current.withState(
                        UsernameRecoveryDeliveryState.REQUESTED, requestedAt, senderReference);
        List<UsernameRecoverySessionCandidate> nextCandidates = replace(index, requested);
        boolean allRegisteredRequested = nextCandidates.stream()
                .filter(UsernameRecoverySessionCandidate::registered)
                .allMatch(c -> c.deliveryState() == UsernameRecoveryDeliveryState.REQUESTED);
        UsernameRecoveryWorkflowStatus nextStatus = allRegisteredRequested
                ? UsernameRecoveryWorkflowStatus.COMPLETED
                : UsernameRecoveryWorkflowStatus.PARTIALLY_REQUESTED;
        return withCandidates(nextCandidates, nextStatus);
    }

    public UsernameRecoveryWorkflowState markFailed(UsernameRecoverySchemeRef schemeRef) {
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

    /** Terminates an open workflow while preserving every candidate delivery fact. */
    public UsernameRecoveryWorkflowState markCancelled() {
        if (status == UsernameRecoveryWorkflowStatus.CANCELLED) {
            return this;
        }
        requireOpenForMutation();
        return new UsernameRecoveryWorkflowState(
                authFlowSessionId, UsernameRecoveryWorkflowStatus.CANCELLED, revision.next(),
                destination, language, candidates);
    }

    public UsernameRecoveryWorkflowState markUnknownOutcome(UsernameRecoverySchemeRef schemeRef) {
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

    private void requireOpenForMutation() {
        if (status != UsernameRecoveryWorkflowStatus.ACTIVE
                && status != UsernameRecoveryWorkflowStatus.PARTIALLY_REQUESTED) {
            throw new IllegalStateException("workflow state is terminal: " + status);
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

    private UsernameRecoveryWorkflowState withCandidate(
            int index,
            UsernameRecoverySessionCandidate updated,
            UsernameRecoveryWorkflowStatus nextStatus) {
        return withCandidates(replace(index, updated), nextStatus);
    }

    private UsernameRecoveryWorkflowState withCandidates(
            List<UsernameRecoverySessionCandidate> nextCandidates,
            UsernameRecoveryWorkflowStatus nextStatus) {
        return new UsernameRecoveryWorkflowState(
                authFlowSessionId,
                nextStatus,
                revision.next(),
                destination,
                language,
                List.copyOf(nextCandidates));
    }

    @Override
    public String toString() {
        return "UsernameRecoveryWorkflowState[authFlowSessionId=<redacted>, status=" + status
                + ", revision=" + revision.value()
                + ", destination=<redacted>, language=" + language
                + ", candidateCount=" + candidates.size() + "]";
    }
}
