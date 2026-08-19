package com.bct.ngtpa.apiservice.domain.model;

/**
 * Delivery state of a Username Recovery candidate.
 *
 * <p>Design references:
 *
 * <ul>
 *   <li>{@code docs/design/6-username-recovery/username-recovery-solution-design.md} sections 2, 7
 *       and 9 (sender {@code ACCEPTED} maps the candidate to {@code REQUESTED}; candidate states
 *       {@code AVAILABLE}, {@code RESERVED}, {@code REQUESTED}, {@code FAILED}, or {@code
 *       UNKNOWN_OUTCOME});
 *   <li>{@code docs/design/6-username-recovery/username-recovery-session-redis-design.md} sections
 *       2-4 (candidate delivery state {@code REQUESTED} after sender acceptance, distinct from
 *       {@code UNKNOWN_OUTCOME} and workflow completion).
 * </ul>
 */
public enum UsernameRecoveryDeliveryState {
  AVAILABLE,
  RESERVED,
  REQUESTED,
  FAILED,
  UNKNOWN_OUTCOME
}
