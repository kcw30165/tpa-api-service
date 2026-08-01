package com.bct.ngtpa.apiservice.application.dto.session.authflow;

import java.util.Objects;

public record RevisionedAuthFlowSessionMutationCommand(
        AuthFlowSessionSnapshot expected,
        AuthFlowSession updated) {
    public RevisionedAuthFlowSessionMutationCommand {
        Objects.requireNonNull(expected, "expected must not be null");
        Objects.requireNonNull(updated, "updated must not be null");
        if (!expected.session().authFlowSessionId().equals(updated.authFlowSessionId())) {
            throw new IllegalArgumentException("auth-flow session identity must not change");
        }
    }
    @Override public String toString() {
        return "RevisionedAuthFlowSessionMutationCommand[expectedRevisionPresent=true, updatedSessionPresent=true]";
    }
}
