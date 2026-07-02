package com.bct.ngtpa.apiservice.application.dto.session.authflow;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.session.SessionConcurrencyMode;
import com.bct.ngtpa.apiservice.application.dto.session.SessionConcurrencyPolicy;
import com.bct.ngtpa.apiservice.application.dto.session.SessionOnNewFlowPolicy;
import com.bct.ngtpa.apiservice.application.dto.session.SessionTtlPolicy;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.AuthFlowConcurrencyDecision;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.StartAuthFlowSessionWithConcurrencyCommand;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.StartAuthFlowSessionWithConcurrencyResult;
import org.junit.jupiter.api.Test;

class AuthFlowSessionConcurrencyModelTest {

    @Test
    void concurrencyResultToStringDoesNotExposeSessionId() {
        var session = new AuthFlowSession(
                new AuthFlowSessionId("afs_secret"),
                AuthFlowScenario.REGISTRATION,
                AuthFlowStatus.STARTED,
                null,
                null,
                AuthFlowSessionData.empty(),
                java.time.Instant.parse("2026-06-29T00:00:00Z"),
                java.time.Instant.parse("2026-06-29T00:00:00Z"),
                java.time.Instant.parse("2026-06-29T00:30:00Z"));

        var result = new StartAuthFlowSessionWithConcurrencyResult(session, 1800, AuthFlowConcurrencyDecision.CREATED_NEW);

        assertThat(result.toString()).doesNotContain("afs_secret");
    }

    @Test
    void concurrencyCommandToStringDoesNotExposeExistingSessionId() {
        var command = new StartAuthFlowSessionWithConcurrencyCommand(
                AuthFlowScenario.REGISTRATION,
                AuthFlowStatus.STARTED,
                new SessionTtlPolicy(true, true, 1800),
                "afs",
                16,
                AuthFlowSessionData.empty(),
                "csrf_hash_secret",
                "otp_secret",
                new AuthFlowSessionId("afs_existing_secret"),
                new SessionConcurrencyPolicy(SessionConcurrencyMode.SINGLE_ACTIVE_FLOW, SessionOnNewFlowPolicy.REPLACE_EXISTING),
                "corr-1");

        assertThat(command.toString())
                .doesNotContain("afs_existing_secret")
                .doesNotContain("csrf_hash_secret")
                .doesNotContain("otp_secret");
    }

    @Test
    void authFlowSessionConcurrencyDecisionDefinesExpectedValues() {
        assertThat(AuthFlowConcurrencyDecision.values()).containsExactly(
                AuthFlowConcurrencyDecision.CREATED_NEW,
                AuthFlowConcurrencyDecision.REPLACED_EXISTING,
                AuthFlowConcurrencyDecision.CREATED_NEW_EXISTING_MISSING,
                AuthFlowConcurrencyDecision.CREATED_NEW_EXISTING_ALREADY_TERMINAL);
    }

    @Test
    void authFlowStatusStillDoesNotUseAmbiguousFailureStates() {
        assertThat(java.util.Arrays.stream(AuthFlowStatus.values()).map(Enum::name))
                .doesNotContain("FAILED", "UNKNOWN", "ERROR");
    }
}
