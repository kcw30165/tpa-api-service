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
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * Verifies the Model B workflow-state mutation command: identity and workflow-state revision
 * compare-and-set invariants, absence of generic session lifecycle fields, and safe redaction.
 * Model B decision {@code MODEL_B_GENERIC_ENVELOPE_TYPED_SIDECAR} (evidence 003 section 3.3): the
 * command is keyed by the canonical {@link AuthFlowSessionId}; the updated workflow-state revision
 * must be exactly expected plus one; no generic session lifecycle fields enter the command.
 */
class UsernameRecoveryWorkflowStateMutationCommandTest {

    private static final AuthFlowSessionId SESSION_ID = new AuthFlowSessionId("afs_opaque-1");

    @Test
    void retainsTypedCommandComponents() {
        UsernameRecoveryWorkflowState updated = sidecar(new UsernameRecoveryWorkflowStateRevision(1));
        UsernameRecoveryWorkflowStateMutationCommand command =
                new UsernameRecoveryWorkflowStateMutationCommand(
                        SESSION_ID, new UsernameRecoveryWorkflowStateRevision(0), updated);
        assertThat(command.authFlowSessionId()).isEqualTo(SESSION_ID);
        assertThat(command.expectedRevision().value()).isZero();
        assertThat(command.updatedState()).isSameAs(updated);
    }

    @Test
    void rejectsNullAuthFlowSessionId() {
        assertThatThrownBy(() -> new UsernameRecoveryWorkflowStateMutationCommand(
                        null, new UsernameRecoveryWorkflowStateRevision(0),
                        sidecar(new UsernameRecoveryWorkflowStateRevision(1))))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("authFlowSessionId must not be null");
    }

    @Test
    void rejectsNullExpectedRevision() {
        assertThatThrownBy(() -> new UsernameRecoveryWorkflowStateMutationCommand(
                        SESSION_ID, null, sidecar(new UsernameRecoveryWorkflowStateRevision(1))))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("expectedRevision must not be null");
    }

    @Test
    void rejectsNullUpdatedState() {
        assertThatThrownBy(() -> new UsernameRecoveryWorkflowStateMutationCommand(
                        SESSION_ID, new UsernameRecoveryWorkflowStateRevision(0), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("updatedState must not be null");
    }

    @Test
    void rejectsCommandIdentityDifferentFromUpdatedStateIdentity() {
        assertThatThrownBy(() -> new UsernameRecoveryWorkflowStateMutationCommand(
                        new AuthFlowSessionId("afs_other"), new UsernameRecoveryWorkflowStateRevision(0),
                        sidecar(new UsernameRecoveryWorkflowStateRevision(1))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("state identity must not change");
    }

    @Test
    void rejectsUpdatedRevisionThatDoesNotAdvanceExactlyOnce() {
        UsernameRecoveryWorkflowState unchanged = sidecar(new UsernameRecoveryWorkflowStateRevision(0));
        assertThatThrownBy(() -> new UsernameRecoveryWorkflowStateMutationCommand(
                        SESSION_ID, new UsernameRecoveryWorkflowStateRevision(0), unchanged))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("updated workflow-state revision must be exactly one greater than expected revision");
    }

    @Test
    void rejectsOverflowedExpectedRevision() {
        assertThatThrownBy(() -> new UsernameRecoveryWorkflowStateMutationCommand(
                        SESSION_ID,
                        new UsernameRecoveryWorkflowStateRevision(Integer.MAX_VALUE),
                        sidecar(new UsernameRecoveryWorkflowStateRevision(Integer.MAX_VALUE))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("updated workflow-state revision must be exactly one greater than expected revision");
    }

    @Test
    void recordCarriesExactlyIdentityExpectedRevisionAndUpdatedState() {
        List<String> names = Arrays.stream(UsernameRecoveryWorkflowStateMutationCommand.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
        assertThat(names).containsExactly("authFlowSessionId", "expectedRevision", "updatedState");
    }

    @Test
    void toStringRedactsSessionIdentityAndUpdatedState() {
        UsernameRecoveryWorkflowStateMutationCommand command =
                new UsernameRecoveryWorkflowStateMutationCommand(
                        SESSION_ID, new UsernameRecoveryWorkflowStateRevision(0),
                        sidecar(new UsernameRecoveryWorkflowStateRevision(1)));
        assertThat(command.toString())
                .doesNotContain("afs_opaque-1", "sch_1", "user-sch")
                .contains("expectedRevision=0");
    }

    private static UsernameRecoveryWorkflowState sidecar(UsernameRecoveryWorkflowStateRevision revision) {
        return new UsernameRecoveryWorkflowState(
                SESSION_ID,
                UsernameRecoveryWorkflowStatus.ACTIVE,
                revision,
                new UsernameRecoveryVerifiedDestination(
                        "p****c@example.com", UsernameRecoveryVerifiedDestination.Channel.EMAIL),
                Locale.ENGLISH,
                List.of(new UsernameRecoverySessionCandidate(
                        new UsernameRecoveryCandidate(
                                new UsernameRecoverySchemeRef("sch_1"),
                                "DB",
                                "DBA",
                                true,
                                "user-sch",
                                UsernameRecoveryDeliveryState.AVAILABLE),
                        null,
                        null)));
    }
}
