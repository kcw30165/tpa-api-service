package com.bct.ngtpa.apiservice.domain.model;

/**
 * Workflow/session status of a Username Recovery flow.
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
public enum UsernameRecoveryWorkflowStatus {
    ACTIVE,
    PARTIALLY_REQUESTED,
    COMPLETED,
    CANCELLED,
    EXPIRED
}
