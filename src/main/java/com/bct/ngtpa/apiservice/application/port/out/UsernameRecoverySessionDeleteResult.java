package com.bct.ngtpa.apiservice.application.port.out;

import java.util.Objects;

/** Typed result of idempotent Username Recovery session deletion. */
public record UsernameRecoverySessionDeleteResult(
        UsernameRecoverySessionMutationOutcome outcome) {

    public UsernameRecoverySessionDeleteResult {
        Objects.requireNonNull(outcome, "outcome must not be null");
        if (outcome != UsernameRecoverySessionMutationOutcome.DELETED
                && outcome != UsernameRecoverySessionMutationOutcome.ALREADY_ABSENT
                && outcome != UsernameRecoverySessionMutationOutcome.SYSTEM_FAILURE) {
            throw new IllegalArgumentException("outcome is not valid for delete");
        }
    }

    @Override
    public String toString() {
        return "UsernameRecoverySessionDeleteResult[outcome=" + outcome + "]";
    }
}
