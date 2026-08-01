package com.bct.ngtpa.apiservice.application.dto.session.authflow;

import java.util.Objects;

public record AuthFlowSessionDeleteResult(AuthFlowSessionMutationOutcome outcome) {
    public AuthFlowSessionDeleteResult {
        Objects.requireNonNull(outcome, "outcome must not be null");
    }
}
