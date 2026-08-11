package com.bct.ngtpa.apiservice.application.dto.session.usernamerecovery;

/**
 * Nonnegative Username Recovery workflow-state revision value object used as the
 * compare-and-set token for sidecar candidate mutations.
 *
 * <p>Model B decision {@code MODEL_B_GENERIC_ENVELOPE_TYPED_SIDECAR} (audit evidence 003 section
 * 3.3): this revision is the candidate/workflow-state CAS counter of the
 * {@link UsernameRecoveryWorkflowState} sidecar record only. It is explicitly NOT an auth-flow
 * session lifecycle revision: the generic envelope owns its own opaque {@code SessionRevision} and
 * the two CAS domains never share tokens.
 *
 * <p>Invariants: the revision is never negative; starts at zero for sidecar creation; {@link #next()}
 * advances by exactly one per sidecar candidate mutation; an overflowed advance wraps negative and
 * is therefore rejected by construction rather than silently producing a negative revision.
 */
public record UsernameRecoveryWorkflowStateRevision(int value) {

    public UsernameRecoveryWorkflowStateRevision {
        if (value < 0) {
            throw new IllegalArgumentException("workflow-state revision must be nonnegative");
        }
    }

    public UsernameRecoveryWorkflowStateRevision next() {
        return new UsernameRecoveryWorkflowStateRevision(value + 1);
    }

    @Override
    public String toString() {
        return "UsernameRecoveryWorkflowStateRevision[value=" + value + "]";
    }
}
