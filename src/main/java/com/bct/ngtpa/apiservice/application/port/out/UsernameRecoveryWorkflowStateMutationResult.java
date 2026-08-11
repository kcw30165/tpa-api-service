package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.session.usernamerecovery.UsernameRecoveryWorkflowState;
import java.util.Objects;

/** Typed result of Username Recovery workflow-state creation or compare-and-set update. */
public record UsernameRecoveryWorkflowStateMutationResult(
        UsernameRecoveryWorkflowStateMutationOutcome outcome,
        UsernameRecoveryWorkflowState state) {

    public UsernameRecoveryWorkflowStateMutationResult {
        Objects.requireNonNull(outcome, "outcome must not be null");
        boolean applied = outcome == UsernameRecoveryWorkflowStateMutationOutcome.CREATED
                || outcome == UsernameRecoveryWorkflowStateMutationOutcome.APPLIED;
        if (applied && state == null) {
            throw new IllegalArgumentException(
                    "created or applied mutation requires the stored state");
        }
        if (!applied && state != null) {
            throw new IllegalArgumentException("non-applied outcome must not carry a state");
        }
        if (!applied
                && outcome != UsernameRecoveryWorkflowStateMutationOutcome.STATE_ALREADY_EXISTS
                && outcome != UsernameRecoveryWorkflowStateMutationOutcome.STATE_NOT_FOUND
                && outcome != UsernameRecoveryWorkflowStateMutationOutcome.REVISION_CONFLICT
                && outcome != UsernameRecoveryWorkflowStateMutationOutcome.SYSTEM_FAILURE) {
            throw new IllegalArgumentException("outcome is not valid for create or update");
        }
    }

    public static UsernameRecoveryWorkflowStateMutationResult outcome(
            UsernameRecoveryWorkflowStateMutationOutcome outcome) {
        return new UsernameRecoveryWorkflowStateMutationResult(outcome, null);
    }

    @Override
    public String toString() {
        return "UsernameRecoveryWorkflowStateMutationResult[outcome=" + outcome
                + ", statePresent=" + (state != null) + "]";
    }
}
