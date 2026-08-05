package com.bct.ngtpa.apiservice.application.dto.session.authflow;

import java.time.Instant;
import java.util.Objects;

public record AuthFlowSession(
        AuthFlowSessionId authFlowSessionId,
        AuthFlowScenario scenario,
        AuthFlowStatus status,
        String csrfTokenHash,
        String activeOtpChallengeId,
        AuthFlowSessionData data,
        Instant createdAt,
        Instant updatedAt,
        Instant expiresAt) {

    public AuthFlowSession {
        Objects.requireNonNull(authFlowSessionId, "authFlowSessionId must not be null");
        Objects.requireNonNull(scenario, "scenario must not be null");
        Objects.requireNonNull(status, "status must not be null");
        data = data == null ? AuthFlowSessionData.empty() : data;
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    }

    public boolean isExpired(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        return !now.isBefore(expiresAt);
    }

    public boolean isTerminal() {
        return status == AuthFlowStatus.COMPLETED
                || status == AuthFlowStatus.CANCELLED
                || status == AuthFlowStatus.LOCKED
                || status == AuthFlowStatus.UNKNOWN_OUTCOME_BLOCKED
                || status == AuthFlowStatus.EXPIRED;
    }

    @Override
    public String toString() {
        return "AuthFlowSession[authFlowSessionId=<redacted>, scenario=" + scenario + ", status=" + status
                + ", csrfTokenHash=<redacted>, activeOtpChallengeId=<redacted>, data=" + data + ", createdAt="
                + createdAt + ", updatedAt=" + updatedAt + ", expiresAt=" + expiresAt + "]";
    }
}
