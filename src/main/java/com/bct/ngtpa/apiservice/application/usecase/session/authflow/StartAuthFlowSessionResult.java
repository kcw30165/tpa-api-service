package com.bct.ngtpa.apiservice.application.usecase.session.authflow;

import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession;
import java.util.Objects;

public record StartAuthFlowSessionResult(AuthFlowSession session, int expiresInSeconds) {
    public StartAuthFlowSessionResult {
        Objects.requireNonNull(session, "session must not be null");
        if (expiresInSeconds <= 0)
            throw new IllegalArgumentException("expiresInSeconds must be positive");
    }
}
