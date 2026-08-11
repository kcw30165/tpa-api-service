package com.bct.ngtpa.apiservice.application.dto.session.usernamerecovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * Verifies the nonnegative Username Recovery workflow-state revision value object used as the
 * candidate compare-and-set token of the Model B sidecar.
 *
 * <p>Model B decision {@code MODEL_B_GENERIC_ENVELOPE_TYPED_SIDECAR} (evidence 003 sections 3.3):
 * the sidecar keeps an explicitly named workflow-state revision ONLY for candidate CAS. It is never
 * an auth-flow session lifecycle revision: the generic envelope owns its own opaque
 * {@code SessionRevision} and the two CAS domains never share tokens.
 *
 * <p>Invariants: the revision is never negative; starts at zero for sidecar creation; {@link #next()}
 * advances by exactly one per sidecar candidate mutation; an overflowed advance wraps negative and
 * is therefore rejected by construction rather than silently producing a negative revision.
 */
class UsernameRecoveryWorkflowStateRevisionTest {

    @Test
    void startsAtZero() {
        assertEquals(0, new UsernameRecoveryWorkflowStateRevision(0).value());
    }

    @Test
    void advancesByExactlyOne() {
        assertEquals(1, new UsernameRecoveryWorkflowStateRevision(0).next().value());
        assertEquals(3, new UsernameRecoveryWorkflowStateRevision(2).next().value());
    }

    @Test
    void rejectsNegative() {
        assertThrows(
                IllegalArgumentException.class, () -> new UsernameRecoveryWorkflowStateRevision(-1));
    }

    @Test
    void nextNeverSilentlyWrapsToNegative() {
        UsernameRecoveryWorkflowStateRevision max =
                new UsernameRecoveryWorkflowStateRevision(Integer.MAX_VALUE);
        assertThrows(IllegalArgumentException.class, max::next);
    }

    @Test
    void toStringDisclosesOnlyTheCounter() {
        assertEquals(
                "UsernameRecoveryWorkflowStateRevision[value=0]",
                new UsernameRecoveryWorkflowStateRevision(0).toString());
    }
}
