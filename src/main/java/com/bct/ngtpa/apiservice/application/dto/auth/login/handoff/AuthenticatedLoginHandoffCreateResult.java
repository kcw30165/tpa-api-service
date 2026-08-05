package com.bct.ngtpa.apiservice.application.dto.auth.login.handoff;

import java.util.Objects;

public record AuthenticatedLoginHandoffCreateResult(
        AuthenticatedLoginHandoffCreateOutcome outcome,
        AuthenticatedLoginHandoff handoff) {
    public AuthenticatedLoginHandoffCreateResult {
        Objects.requireNonNull(outcome);
        if ((outcome == AuthenticatedLoginHandoffCreateOutcome.CREATED
                || outcome == AuthenticatedLoginHandoffCreateOutcome.EXISTING) && handoff == null)
            throw new IllegalArgumentException("handoff required");
    }
}
