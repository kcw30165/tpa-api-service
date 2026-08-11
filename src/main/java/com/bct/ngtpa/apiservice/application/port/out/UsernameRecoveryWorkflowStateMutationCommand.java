package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.usernamerecovery.UsernameRecoveryWorkflowState;
import com.bct.ngtpa.apiservice.application.dto.session.usernamerecovery.UsernameRecoveryWorkflowStateRevision;
import java.util.Objects;

/**
 * Compare-and-set mutation command for the Model B Username Recovery workflow-state sidecar.
 *
 * <p>Model B decision {@code MODEL_B_GENERIC_ENVELOPE_TYPED_SIDECAR}: the command is keyed by the
 * canonical {@link AuthFlowSessionId}; the updated workflow-state revision must be exactly the
 * expected revision plus one; no generic session lifecycle fields (scenario, CSRF hash,
 * timestamps, expiry, generic revision) may enter the command.
 */
public record UsernameRecoveryWorkflowStateMutationCommand(
        AuthFlowSessionId authFlowSessionId,
        UsernameRecoveryWorkflowStateRevision expectedRevision,
        UsernameRecoveryWorkflowState updatedState) {

    public UsernameRecoveryWorkflowStateMutationCommand {
        Objects.requireNonNull(authFlowSessionId, "authFlowSessionId must not be null");
        Objects.requireNonNull(expectedRevision, "expectedRevision must not be null");
        Objects.requireNonNull(updatedState, "updatedState must not be null");
        if (!authFlowSessionId.equals(updatedState.authFlowSessionId())) {
            throw new IllegalArgumentException("state identity must not change");
        }
        if (expectedRevision.value() == Integer.MAX_VALUE
                || updatedState.revision().value() != expectedRevision.value() + 1) {
            throw new IllegalArgumentException(
                    "updated workflow-state revision must be exactly one greater than expected revision");
        }
    }

    @Override
    public String toString() {
        return "UsernameRecoveryWorkflowStateMutationCommand[authFlowSessionId=, expectedRevision="
                + expectedRevision.value() + ", updatedState=]";
    }
}
