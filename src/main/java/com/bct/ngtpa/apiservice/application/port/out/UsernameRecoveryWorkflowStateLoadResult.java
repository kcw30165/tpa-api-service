package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.session.usernamerecovery.UsernameRecoveryWorkflowState;
import java.util.Objects;

/** Typed load result distinguishing loaded, absent, and infrastructure-failure outcomes. */
public record UsernameRecoveryWorkflowStateLoadResult(
        UsernameRecoveryWorkflowStateMutationOutcome outcome,
        UsernameRecoveryWorkflowState state) {

    public UsernameRecoveryWorkflowStateLoadResult {
        Objects.requireNonNull(outcome, "outcome must not be null");
        boolean loaded = outcome == UsernameRecoveryWorkflowStateMutationOutcome.LOADED;
        if (loaded && state == null) {
            throw new IllegalArgumentException("loaded outcome requires a state");
        }
        if (!loaded && state != null) {
            throw new IllegalArgumentException("non-loaded outcome must not carry a state");
        }
        if (!loaded
                && outcome != UsernameRecoveryWorkflowStateMutationOutcome.STATE_NOT_FOUND
                && outcome != UsernameRecoveryWorkflowStateMutationOutcome.SYSTEM_FAILURE) {
            throw new IllegalArgumentException("outcome is not valid for load");
        }
    }

    public static UsernameRecoveryWorkflowStateLoadResult loaded(
            UsernameRecoveryWorkflowState state) {
        return new UsernameRecoveryWorkflowStateLoadResult(
                UsernameRecoveryWorkflowStateMutationOutcome.LOADED, state);
    }

    public static UsernameRecoveryWorkflowStateLoadResult notFound() {
        return new UsernameRecoveryWorkflowStateLoadResult(
                UsernameRecoveryWorkflowStateMutationOutcome.STATE_NOT_FOUND, null);
    }

    public static UsernameRecoveryWorkflowStateLoadResult systemFailure() {
        return new UsernameRecoveryWorkflowStateLoadResult(
                UsernameRecoveryWorkflowStateMutationOutcome.SYSTEM_FAILURE, null);
    }

    @Override
    public String toString() {
        return "UsernameRecoveryWorkflowStateLoadResult[outcome=" + outcome
                + ", statePresent=" + (state != null) + "]";
    }
}
