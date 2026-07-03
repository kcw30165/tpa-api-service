package com.bct.ngtpa.apiservice.application.dto.session.authflow;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.session.SessionTtlPolicy;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.StartAuthFlowSessionCommand;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.UpdateAuthFlowOtpCommand;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.UpdateAuthFlowUsernameCheckCommand;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AuthFlowSessionLifecycleCommandTest {

    @Test
    void lifecycleServiceDoesNotExposeSensitiveValuesInExceptionsOrToString() {
        var id = new AuthFlowSessionId("afs_secret");
        var usernameCheck = new UsernameCheckState(new SelectedTrust("TL", "ENV1", null, null, null), "user_secret", Instant.parse("2026-06-29T00:00:00Z"), Instant.parse("2026-06-29T00:10:00Z"));

        var start = new StartAuthFlowSessionCommand(AuthFlowScenario.REGISTRATION, AuthFlowStatus.STARTED, new SessionTtlPolicy(true, true, 1800), "afs", 16, AuthFlowSessionData.empty(), "csrf_hash_secret", "otp_secret", "corr-1");
        var otp = new UpdateAuthFlowOtpCommand(id, "otp_secret", "corr-1");
        var username = new UpdateAuthFlowUsernameCheckCommand(id, usernameCheck, "corr-1");

        assertThat(start.toString()).doesNotContain("csrf_hash_secret", "otp_secret");
        assertThat(otp.toString()).doesNotContain("afs_secret", "otp_secret");
        assertThat(username.toString()).doesNotContain("afs_secret", "user_secret");
    }

    @Test
    void authFlowStatusStillDoesNotUseAmbiguousFailureStates() {
        assertThat(java.util.Arrays.stream(AuthFlowStatus.values()).map(Enum::name))
                .doesNotContain("FAILED", "UNKNOWN", "ERROR");
    }
}
