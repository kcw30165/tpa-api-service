package com.bct.ngtpa.apiservice.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Verifies the exact Username Recovery workflow/session-status vocabulary required by the
 * approved final design.
 *
 * <p>Design references:
 *
 * <ul>
 *   <li>{@code docs/design/6-username-recovery/username-recovery-session-redis-design.md}
 *       section 2 lifecycle ({@code ACTIVE -> PARTIALLY_REQUESTED -> COMPLETED};
 *       {@code ACTIVE} or {@code PARTIALLY_REQUESTED} -> {@code CANCELLED} or {@code EXPIRED});</li>
 *   <li>{@code docs/design/6-username-recovery/username-recovery-solution-design.md}
 *       section 7 (same lifecycle);</li>
 *   <li>{@code docs/design/6-username-recovery/username-recovery-api-contract-design.md}
 *       section 5 (result data {@code status} {@code PARTIALLY_REQUESTED} / {@code COMPLETED});</li>
 *   <li>{@code docs/design/6-username-recovery/handover.md} Username Recovery ownership
 *       ({@code ACTIVE}, {@code PARTIALLY_REQUESTED}, {@code COMPLETED}).</li>
 * </ul>
 */
class UsernameRecoveryWorkflowStatusTest {

    @Test
    void definesExactApprovedWorkflowStatusVocabulary() {
        assertEquals(
                List.of(
                        UsernameRecoveryWorkflowStatus.ACTIVE,
                        UsernameRecoveryWorkflowStatus.PARTIALLY_REQUESTED,
                        UsernameRecoveryWorkflowStatus.COMPLETED,
                        UsernameRecoveryWorkflowStatus.CANCELLED,
                        UsernameRecoveryWorkflowStatus.EXPIRED),
                List.of(UsernameRecoveryWorkflowStatus.values()));
    }

    @Test
    void keepsWorkflowStatusVocabularyDisjointFromDeliveryStateVocabulary() {
        // Workflow completion is not a delivery state, and candidate REQUESTED is not a workflow status.
        Set<String> deliveryStateNames = Arrays.stream(UsernameRecoveryDeliveryState.values())
                .map(Enum::name)
                .collect(Collectors.toSet());
        for (UsernameRecoveryWorkflowStatus status : UsernameRecoveryWorkflowStatus.values()) {
            assertFalse(
                    deliveryStateNames.contains(status.name()),
                    status.name() + " must not be part of the delivery-state vocabulary");
        }

        Set<String> workflowStatusNames = Arrays.stream(UsernameRecoveryWorkflowStatus.values())
                .map(Enum::name)
                .collect(Collectors.toSet());
        for (UsernameRecoveryDeliveryState state : UsernameRecoveryDeliveryState.values()) {
            assertFalse(
                    workflowStatusNames.contains(state.name()),
                    state.name() + " must not be part of the workflow-status vocabulary");
        }
    }
}
