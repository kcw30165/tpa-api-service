package com.bct.ngtpa.apiservice.application.dto.session.authflow;

import java.time.Instant;
import java.util.Objects;

public record AuthFlowSessionCreateCommand(
        AuthFlowSessionId authFlowSessionId,
        AuthFlowScenario scenario,
        AuthFlowStatus initialStatus,
        Instant createdAt,
        Instant expiresAt,
        AuthFlowSessionData data,
        String csrfTokenHash,
        String activeOtpChallengeId) {

    public AuthFlowSessionCreateCommand {
        Objects.requireNonNull(authFlowSessionId, "authFlowSessionId must not be null");
        Objects.requireNonNull(scenario, "scenario must not be null");
        Objects.requireNonNull(initialStatus, "initialStatus must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        data = data == null ? AuthFlowSessionData.empty() : data;
    }

    @Override
    public String toString() {
        return "AuthFlowSessionCreateCommand[authFlowSessionId=<redacted>, scenario=" + scenario + ", initialStatus=" + initialStatus + ", createdAt=" + createdAt + ", expiresAt=" + expiresAt + ", data=" + data + ", csrfTokenHash=<redacted>, activeOtpChallengeId=<redacted>]";
    }
}
