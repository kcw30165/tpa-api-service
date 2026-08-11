package com.bct.ngtpa.apiservice.application.port.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.usernamerecovery.UsernameRecoveryWorkflowState;
import com.bct.ngtpa.apiservice.application.dto.session.usernamerecovery.UsernameRecoveryWorkflowStateRevision;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryCandidate;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryDeliveryState;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySchemeRef;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySessionCandidate;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryVerifiedDestination;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryWorkflowStatus;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class UsernameRecoveryWorkflowStateCreateCommandTest {

    @Test
    void carriesStateAndEnvelopeExpiryWithoutDuplicatingIdentity() {
        var state = state();
        var expiry = Instant.parse("2026-08-08T00:10:00Z");

        var command = new UsernameRecoveryWorkflowStateCreateCommand(state, expiry);

        assertThat(command.state()).isSameAs(state);
        assertThat(command.expiresAt()).isEqualTo(expiry);
        assertThat(Arrays.stream(command.getClass().getRecordComponents()).map(c -> c.getName()))
                .containsExactly("state", "expiresAt");
    }

    @Test
    void rejectsNullStateAndExpiry() {
        assertThatThrownBy(() -> new UsernameRecoveryWorkflowStateCreateCommand(
                null, Instant.parse("2026-08-08T00:10:00Z")))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new UsernameRecoveryWorkflowStateCreateCommand(state(), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void acceptsElapsedExpiryForAdapterClockValidation() {
        var expiry = Instant.parse("2000-01-01T00:00:00Z");
        assertThat(new UsernameRecoveryWorkflowStateCreateCommand(state(), expiry).expiresAt())
                .isEqualTo(expiry);
    }

    @Test
    void toStringRedactsProtectedWorkflowState() {
        var text = new UsernameRecoveryWorkflowStateCreateCommand(
                state(), Instant.parse("2026-08-08T00:10:00Z")).toString();
        assertThat(text)
                .contains("state=<redacted>")
                .doesNotContain("afs_abc123", "member@example.com", "user-sch", "sch_opaque-1");
    }

    private static UsernameRecoveryWorkflowState state() {
        var candidate = new UsernameRecoverySessionCandidate(
                new UsernameRecoveryCandidate(
                        new UsernameRecoverySchemeRef("sch_opaque-1"),
                        "HK", "trust-1", true, "user-sch",
                        UsernameRecoveryDeliveryState.AVAILABLE),
                null, null);
        return new UsernameRecoveryWorkflowState(
                new AuthFlowSessionId("afs_abc123"),
                UsernameRecoveryWorkflowStatus.ACTIVE,
                new UsernameRecoveryWorkflowStateRevision(0),
                new UsernameRecoveryVerifiedDestination(
                        "member@example.com", UsernameRecoveryVerifiedDestination.Channel.EMAIL),
                Locale.forLanguageTag("zh-HK"),
                List.of(candidate));
    }
}
