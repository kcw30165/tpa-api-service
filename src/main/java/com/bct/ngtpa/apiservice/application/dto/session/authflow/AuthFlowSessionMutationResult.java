package com.bct.ngtpa.apiservice.application.dto.session.authflow;

import java.util.Objects;

public record AuthFlowSessionMutationResult(
        AuthFlowSessionMutationOutcome outcome,
        AuthFlowSessionSnapshot snapshot) {
    public AuthFlowSessionMutationResult {
        Objects.requireNonNull(outcome, "outcome must not be null");
        if (outcome == AuthFlowSessionMutationOutcome.APPLIED && snapshot == null) {
            throw new IllegalArgumentException("applied mutation requires snapshot");
        }
    }
    public static AuthFlowSessionMutationResult outcome(AuthFlowSessionMutationOutcome outcome) {
        return new AuthFlowSessionMutationResult(outcome, null);
    }
    @Override public String toString() {
        return "AuthFlowSessionMutationResult[outcome=" + outcome
                + ", snapshotPresent=" + (snapshot != null) + "]";
    }
}
