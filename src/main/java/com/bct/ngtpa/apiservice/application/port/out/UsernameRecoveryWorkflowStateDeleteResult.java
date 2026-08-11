package com.bct.ngtpa.apiservice.application.port.out;

import java.util.Objects;

/** Typed result of idempotent Username Recovery workflow-state deletion. */
public record UsernameRecoveryWorkflowStateDeleteResult(
        UsernameRecoveryWorkflowStateMutationOutcome outcome) {

    public UsernameRecoveryWorkflowStateDeleteResult {
        Objects.requireNonNull(outcome, "outcome must not be null");
        if (outcome != UsernameRecoveryWorkflowStateMutationOutcome.DELETED
                && outcome != UsernameRecoveryWorkflowStateMutationOutcome.ALREADY_ABSENT
                && outcome != UsernameRecoveryWorkflowStateMutationOutcome.SYSTEM_FAILURE) {
            throw new IllegalArgumentException("outcome is not valid for delete");
        }
    }

    @Override
    public String toString() {
        return "UsernameRecoveryWorkflowStateDeleteResult[outcome=" + outcome + "]";
    }
}
