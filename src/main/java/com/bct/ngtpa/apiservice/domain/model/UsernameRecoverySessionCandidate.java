package com.bct.ngtpa.apiservice.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Protected session-scoped Username Recovery candidate.
 *
 * <p>Design references:
 *
 * <ul>
 *   <li>{@code docs/design/6-username-recovery/username-recovery-session-redis-design.md}
 *       section 3 (candidate fields: Scheme-Ref, account environment, trust code, registration flag,
 *       username only when registered, delivery state, accepted timestamp, sender/delivery operation
 *       reference when available);</li>
 *   <li>{@code docs/design/6-username-recovery/username-recovery-solution-design.md}
 *       section 7 (requested timestamp and sender/idempotency reference required for replay
 *       safety);</li>
 *   <li>{@code docs/design/6-username-recovery/username-recovery-api-contract-design.md}
 *       section 4 (session-bound opaque schemeRefs, including for unregistered schemes).</li>
 * </ul>
 *
 * <p>The Task 006 {@link UsernameRecoveryCandidate} is reused by composition; this session candidate
 * adds delivery metadata (requested timestamp and sender reference) that the committed Task 006
 * projection does not carry, and additionally requires a session-bound (non-null) Scheme-Ref.
 *
 * <p>Invariants: {@code candidate} is non-null with a non-null Scheme-Ref; a {@code REQUESTED}
 * candidate requires a non-null {@code requestedAt} and a nonblank {@code senderReference}; any
 * other delivery state must not retain delivery metadata. {@link #toString()} never discloses the
 * Scheme-Ref, username, or sender reference.
 */
public record UsernameRecoverySessionCandidate(
        UsernameRecoveryCandidate candidate,
        Instant requestedAt,
        String senderReference) {

    public UsernameRecoverySessionCandidate {
        Objects.requireNonNull(candidate, "candidate must not be null");
        if (candidate.schemeRef() == null) {
            throw new IllegalArgumentException("session candidate requires a session-bound schemeRef");
        }
        if (candidate.deliveryState() == UsernameRecoveryDeliveryState.REQUESTED) {
            Objects.requireNonNull(requestedAt, "requested candidate requires a requestedAt timestamp");
            Objects.requireNonNull(senderReference, "requested candidate requires a senderReference");
            if (senderReference.isBlank()) {
                throw new IllegalArgumentException("senderReference must not be blank");
            }
        } else if (requestedAt != null || senderReference != null) {
            throw new IllegalArgumentException("non-requested candidate must not retain delivery metadata");
        }
    }

    public UsernameRecoverySchemeRef schemeRef() {
        return candidate.schemeRef();
    }

    public String accountEnv() {
        return candidate.accountEnv();
    }

    public String trustCode() {
        return candidate.trustCode();
    }

    public boolean registered() {
        return candidate.registered();
    }

    public String userName() {
        return candidate.userName();
    }

    public UsernameRecoveryDeliveryState deliveryState() {
        return candidate.deliveryState();
    }

    /**
     * Rebuilds this candidate with a new delivery state and optional delivery metadata, preserving
     * all Task 006 candidate fields.
     */
    public UsernameRecoverySessionCandidate withState(
            UsernameRecoveryDeliveryState state, Instant requestedAt, String senderReference) {
        UsernameRecoveryCandidate rebuilt = new UsernameRecoveryCandidate(
                candidate.schemeRef(),
                candidate.accountEnv(),
                candidate.trustCode(),
                candidate.registered(),
                candidate.userName(),
                state);
        return new UsernameRecoverySessionCandidate(rebuilt, requestedAt, senderReference);
    }

    @Override
    public String toString() {
        return "UsernameRecoverySessionCandidate[candidate=" + candidate
                + ", requestedAt=" + requestedAt
                + ", senderReference=" + (senderReference == null ? null : "<redacted>") + "]";
    }
}
