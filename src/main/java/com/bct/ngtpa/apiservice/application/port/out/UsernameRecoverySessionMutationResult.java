package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySession;
import java.util.Objects;

/** Typed result of Username Recovery session creation or compare-and-set update. */
public record UsernameRecoverySessionMutationResult(
        UsernameRecoverySessionMutationOutcome outcome,
        UsernameRecoverySession session) {

    public UsernameRecoverySessionMutationResult {
        Objects.requireNonNull(outcome, "outcome must not be null");
        boolean applied = outcome == UsernameRecoverySessionMutationOutcome.CREATED
                || outcome == UsernameRecoverySessionMutationOutcome.APPLIED;
        if (applied && session == null) {
            throw new IllegalArgumentException(
                    "created or applied mutation requires the stored session");
        }
        if (!applied && session != null) {
            throw new IllegalArgumentException("non-applied outcome must not carry a session");
        }
        if (!applied
                && outcome != UsernameRecoverySessionMutationOutcome.SESSION_NOT_FOUND
                && outcome != UsernameRecoverySessionMutationOutcome.REVISION_CONFLICT
                && outcome != UsernameRecoverySessionMutationOutcome.SYSTEM_FAILURE) {
            throw new IllegalArgumentException("outcome is not valid for create or update");
        }
    }

    public static UsernameRecoverySessionMutationResult outcome(
            UsernameRecoverySessionMutationOutcome outcome) {
        return new UsernameRecoverySessionMutationResult(outcome, null);
    }

    @Override
    public String toString() {
        return "UsernameRecoverySessionMutationResult[outcome=" + outcome
                + ", sessionPresent=" + (session != null) + "]";
    }
}
