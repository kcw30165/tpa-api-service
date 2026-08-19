package com.bct.ngtpa.apiservice.application.port.out;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Verifies the Username Recovery idempotency reserve outcome vocabulary: exactly one outcome for
 * each reserve decision in the outcome matrix, including stable unknown outcome and infrastructure
 * failure.
 *
 * <p>Design references: {@code
 * docs/design/6-username-recovery/username-recovery-session-redis-design.md} section 4 (reservation
 * ownership/idempotency operation) and section 8 (UNKNOWN_OUTCOME is never blindly resent; Redis
 * failures are never treated as success). See evidence {@code 004-outcome-matrix.txt}.
 */
class UsernameRecoveryIdempotencyOutcomeTest {

  @Test
  void exposesRequiredReserveOutcomes() {
    assertThat(UsernameRecoveryIdempotencyOutcome.values())
        .containsExactly(
            UsernameRecoveryIdempotencyOutcome.RESERVED,
            UsernameRecoveryIdempotencyOutcome.DUPLICATE_IN_PROGRESS,
            UsernameRecoveryIdempotencyOutcome.COMPLETED_REPLAY,
            UsernameRecoveryIdempotencyOutcome.KEY_REUSED_WITH_DIFFERENT_REQUEST,
            UsernameRecoveryIdempotencyOutcome.UNKNOWN_OUTCOME_EXISTING,
            UsernameRecoveryIdempotencyOutcome.SYSTEM_FAILURE);
  }
}
