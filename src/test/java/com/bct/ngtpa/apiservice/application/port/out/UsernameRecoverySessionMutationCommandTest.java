package com.bct.ngtpa.apiservice.application.port.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryCandidate;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryCsrfHash;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryDeliveryState;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySchemeRef;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySession;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySessionCandidate;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySessionId;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySessionRevision;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryVerifiedDestination;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryWorkflowStatus;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * Verifies the compare-and-set Username Recovery session mutation command: it carries the expected
 * revision so concurrent snapshots cannot both commit, requires the updated aggregate, and rejects
 * a session identity change.
 *
 * <p>Design references: {@code docs/design/6-username-recovery/username-recovery-session-redis-design.md}
 * section 4 (reacquire the session mutation boundary; persist candidate state, session status,
 * revision, and replay snapshot consistently) and {@code .cline/tasks/011-session-outbound-ports.md}
 * (carry expected revision for compare-and-set mutation so concurrent snapshots cannot both commit;
 * redact sensitive values). Mirrors {@code RevisionedAuthFlowSessionMutationCommand} identity
 * consistency semantics.
 */
class UsernameRecoverySessionMutationCommandTest {

    @Test
    void requiresAllFields() {
        assertThatThrownBy(() -> new UsernameRecoverySessionMutationCommand(
                null, new UsernameRecoverySessionRevision(1), activeSession()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new UsernameRecoverySessionMutationCommand(
                new UsernameRecoverySessionId("afs_opaque-1"), null, activeSession()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new UsernameRecoverySessionMutationCommand(
                new UsernameRecoverySessionId("afs_opaque-1"), new UsernameRecoverySessionRevision(1), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsChangedSessionIdentity() {
        assertThatThrownBy(() -> new UsernameRecoverySessionMutationCommand(
                new UsernameRecoverySessionId("afs_opaque-other"),
                new UsernameRecoverySessionRevision(1),
                activeSession()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("session identity must not change");
    }

    @Test
    void carriesExpectedRevisionForCompareAndSet() {
        UsernameRecoverySession updated = activeSession().reserve(new UsernameRecoverySchemeRef("sch_1"));
        UsernameRecoverySessionMutationCommand command = new UsernameRecoverySessionMutationCommand(
                updated.sessionId(), new UsernameRecoverySessionRevision(0), updated);

        assertThat(command.expectedRevision().value()).isZero();
        assertThat(command.updatedSession()).isSameAs(updated);
    }

    @Test
    void toStringRedactsSessionIdAndUpdatedSession() {
        UsernameRecoverySession updated = activeSession().reserve(new UsernameRecoverySchemeRef("sch_1"));
        UsernameRecoverySessionMutationCommand command = new UsernameRecoverySessionMutationCommand(
                updated.sessionId(), new UsernameRecoverySessionRevision(0), updated);

        assertThat(command.toString())
                .contains("expectedRevision=0")
                .doesNotContain("afs_opaque", "sch_1", "user-sch", "updatedSession=UsernameRecoverySession[sessionId=");
    }

    @Test
    void rejectsUpdatedSessionRevisionThatDoesNotAdvanceExactlyOnce() {
        UsernameRecoverySession unchanged = activeSession();
        assertThatThrownBy(() -> new UsernameRecoverySessionMutationCommand(
                unchanged.sessionId(), new UsernameRecoverySessionRevision(0), unchanged))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "updated session revision must be exactly one greater than expected revision");
    }

    private static UsernameRecoverySession activeSession() {
        return new UsernameRecoverySession(
                new UsernameRecoverySessionId("afs_opaque-1"),
                UsernameRecoverySession.Scenario.USERNAME_RECOVERY,
                UsernameRecoveryWorkflowStatus.ACTIVE,
                new UsernameRecoverySessionRevision(0),
                new UsernameRecoveryCsrfHash("csrf-hash-1"),
                new UsernameRecoveryVerifiedDestination(
                        "p****c@example.com", UsernameRecoveryVerifiedDestination.Channel.EMAIL),
                Locale.ENGLISH,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:10:00Z"),
                List.of(new UsernameRecoverySessionCandidate(
                        new UsernameRecoveryCandidate(
                                new UsernameRecoverySchemeRef("sch_1"),
                                "DB",
                                "DBA",
                                true,
                                "user-sch_1",
                                UsernameRecoveryDeliveryState.AVAILABLE),
                        null,
                        null)));
    }
}
