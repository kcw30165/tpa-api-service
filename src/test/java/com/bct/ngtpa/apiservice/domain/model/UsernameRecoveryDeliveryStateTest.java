package com.bct.ngtpa.apiservice.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Verifies the exact Username Recovery delivery-state vocabulary required by the approved final
 * design.
 *
 * <p>Design references:
 *
 * <ul>
 *   <li>{@code docs/design/6-username-recovery/username-recovery-session-redis-design.md} sections
 *       2-4 (candidate delivery state {@code REQUESTED} after sender acceptance, distinct from
 *       {@code UNKNOWN_OUTCOME} and workflow completion);
 *   <li>{@code docs/design/6-username-recovery/username-recovery-solution-design.md} sections 2, 7
 *       and 9 (sender {@code ACCEPTED} maps the candidate to {@code REQUESTED});
 *   <li>{@code docs/design/6-username-recovery/username-recovery-api-contract-design.md} error
 *       matrix UR-REQ-005/006/008 (candidate {@code REQUESTED}).
 * </ul>
 */
class UsernameRecoveryDeliveryStateTest {

  @Test
  void definesExactApprovedDeliveryStateVocabulary() {
    assertEquals(
        List.of(
            UsernameRecoveryDeliveryState.AVAILABLE,
            UsernameRecoveryDeliveryState.RESERVED,
            UsernameRecoveryDeliveryState.REQUESTED,
            UsernameRecoveryDeliveryState.FAILED,
            UsernameRecoveryDeliveryState.UNKNOWN_OUTCOME),
        List.of(UsernameRecoveryDeliveryState.values()));
  }

  @Test
  void keepsSenderAcceptanceDistinctFromUnknownOutcomeAndFailure() {
    assertNotEquals(
        UsernameRecoveryDeliveryState.REQUESTED, UsernameRecoveryDeliveryState.UNKNOWN_OUTCOME);
    assertNotEquals(UsernameRecoveryDeliveryState.REQUESTED, UsernameRecoveryDeliveryState.FAILED);
    assertNotEquals(
        UsernameRecoveryDeliveryState.UNKNOWN_OUTCOME, UsernameRecoveryDeliveryState.FAILED);
  }
}
