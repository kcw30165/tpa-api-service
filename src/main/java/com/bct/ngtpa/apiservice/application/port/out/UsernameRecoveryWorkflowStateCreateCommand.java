package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.session.usernamerecovery.UsernameRecoveryWorkflowState;
import java.time.Instant;
import java.util.Objects;

/**
 * Creation command for the Model B Username Recovery workflow-state sidecar.
 *
 * <p>The generic auth-flow envelope remains authoritative for expiry. This command carries that
 * expiry across the persistence boundary solely so the Redis adapter can apply a bounded initial
 * TTL. Expiry is not part of the sidecar aggregate.
 */
public record UsernameRecoveryWorkflowStateCreateCommand(
        UsernameRecoveryWorkflowState state,
        Instant expiresAt) {

    public UsernameRecoveryWorkflowStateCreateCommand {
        Objects.requireNonNull(state, "state must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    }

    @Override
    public String toString() {
        return "UsernameRecoveryWorkflowStateCreateCommand[state=<redacted>, expiresAt="
                + expiresAt + "]";
    }
}
