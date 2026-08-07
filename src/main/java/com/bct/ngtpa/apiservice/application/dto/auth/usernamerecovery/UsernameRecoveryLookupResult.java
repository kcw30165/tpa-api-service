package com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery;

import java.util.List;
import java.util.Objects;

/**
 * Application result of the Username Recovery member lookup boundary.
 *
 * <p>Design references: {@code docs/design/6-username-recovery/username-recovery-apim-integration-design.md}
 * section 8 (outcomes {@code MATCHED}, {@code NOT_FOUND}, {@code CONTRACT_INVALID},
 * {@code UNAVAILABLE}, and {@code UNKNOWN_OUTCOME}; downstream rejections never become public) and
 * section 10 (the application port exposes domain outcomes, not APIM envelopes or DTOs).
 *
 * <p>Invariants: {@code status} is required. {@code MATCHED} requires at least one candidate; all
 * other statuses must not contain candidates. {@link #toString()} never discloses candidate
 * usernames.
 */
public record UsernameRecoveryLookupResult(
        Status status,
        List<UsernameRecoveryLookupCandidate> candidates) {

    public enum Status {
        MATCHED,
        NOT_FOUND,
        CONTRACT_INVALID,
        UNAVAILABLE,
        UNKNOWN_OUTCOME
    }

    public UsernameRecoveryLookupResult {
        Objects.requireNonNull(status, "status must not be null");
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
        if (status == Status.MATCHED && candidates.isEmpty()) {
            throw new IllegalArgumentException("matched lookup requires at least one candidate");
        }
        if (status != Status.MATCHED && !candidates.isEmpty()) {
            throw new IllegalArgumentException("non-matched lookup must not contain candidates");
        }
    }

    @Override
    public String toString() {
        return "UsernameRecoveryLookupResult[status=" + status
                + ", candidateCount=" + candidates.size() + "]";
    }
}
