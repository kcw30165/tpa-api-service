package com.bct.ngtpa.apiservice.application.usecase.session.authflow;

import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession;
import java.util.Objects;

public record StartAuthFlowSessionWithConcurrencyResult(AuthFlowSession session, int expiresInSeconds, AuthFlowConcurrencyDecision decision) {
    public StartAuthFlowSessionWithConcurrencyResult {
        Objects.requireNonNull(session, "session must not be null");
        if (expiresInSeconds <= 0) {
            throw new IllegalArgumentException("expiresInSeconds must be positive");
        }
        Objects.requireNonNull(decision, "decision must not be null");
    }

    @Override
    public String toString() {
        return "StartAuthFlowSessionWithConcurrencyResult[session=<redacted>, expiresInSeconds="
                + expiresInSeconds + ", decision=" + decision + "]";
    }
}
