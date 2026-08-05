package com.bct.ngtpa.apiservice.application.dto.session.authflow;

import java.util.Objects;

public record RevisionedAuthFlowSessionDeleteCommand(AuthFlowSessionSnapshot expected) {
    public RevisionedAuthFlowSessionDeleteCommand {
        Objects.requireNonNull(expected, "expected must not be null");
    }
    @Override public String toString() {
        return "RevisionedAuthFlowSessionDeleteCommand[expectedRevisionPresent=true]";
    }
}
